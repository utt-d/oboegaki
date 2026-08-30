package jp.oboegaki.core.data

import jp.oboegaki.core.domain.GroupPlacementDecision
import jp.oboegaki.core.domain.GroupPolicy
import jp.oboegaki.core.domain.OrderingPolicy
import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemRelation

/**
 * The result of the deterministic, side-effect-free backup preflight.
 *
 * A backup may have been edited by hand or produced by an older build.  This
 * helper deliberately runs before any database transaction so that a bad
 * backup can never leave a partially replaced database behind.
 */
data class BackupNormalizationResult(
    val items: List<AppItem>,
    val relations: List<ItemRelation>,
    val duplicateItemIds: Int = 0,
    val duplicateRelationIds: Int = 0,
    val correctedGroupReferences: Int = 0,
    val correctedParentReferences: Int = 0,
    val correctedConversionReferences: Int = 0,
    val correctedRelations: Int = 0,
)

/**
 * Normalizes backup IDs and references without touching Room.
 *
 * The first occurrence of an item ID is canonical. Later occurrences get a
 * stable suffix and remain separate items. References always point to the
 * canonical first occurrence; this is the only deterministic interpretation
 * available when a source backup contains duplicate IDs.
 */
internal fun normalizeBackupData(
    sourceItems: List<AppItem>,
    sourceRelations: List<ItemRelation>,
): BackupNormalizationResult {
    // Keep this helper safe when called directly as well as through the
    // repository's import preflight. Invalid records never receive an ID.
    val candidates = sourceItems.filter(::isBackupItemImportable)
    val canonicalIds = linkedMapOf<String, String>()
    val usedItemIds = candidates.mapTo(linkedSetOf()) { it.id }
    val seenItemIds = mutableMapOf<String, Int>()
    val assignedItemIds = candidates.map { item ->
        val occurrence = (seenItemIds[item.id] ?: 0) + 1
        seenItemIds[item.id] = occurrence
        if (occurrence == 1) {
            canonicalIds[item.id] = item.id
            item.id
        } else {
            stableDuplicateId(item.id, occurrence, usedItemIds)
        }
    }

    val itemByAssignedId = candidates.mapIndexed { index, item -> assignedItemIds[index] to item }.toMap()
    val duplicateItemIds = candidates.size - canonicalIds.size
    var correctedGroups = 0
    var correctedParents = 0
    var correctedConversions = 0
    val provisional = candidates.mapIndexed { index, source ->
        val id = assignedItemIds[index]
        val groupId = source.groupId?.let { reference ->
            val canonical = canonicalIds[reference]
            if (canonical == null) correctedGroups++
            canonical
        }
        val parentId = source.parentId?.let { reference ->
            val canonical = canonicalIds[reference]
            val parent = canonical?.let(itemByAssignedId::get)
            if (canonical == null || canonical == id || parent?.kind != ItemKind.TODO || source.kind != ItemKind.TODO) {
                correctedParents++
                null
            } else canonical
        }
        val convertedFromId = source.convertedFromId?.let { reference ->
            val canonical = canonicalIds[reference]
            val convertedFrom = canonical?.let(itemByAssignedId::get)
            if (canonical == null || canonical == id || source.kind != ItemKind.TODO || convertedFrom?.kind != ItemKind.MEMO) {
                correctedConversions++
                null
            } else canonical
        }
        source.copy(id = id, groupId = groupId, parentId = parentId, convertedFromId = convertedFromId)
    }

    // Group references need the complete remapped list to detect cycles and
    // invalid parents.  Invalid groups are rooted rather than discarded.
    val normalizedItems = provisional.map { item ->
        if (item.groupId != null &&
            GroupPolicy.validatePlacement(item, item.groupId, provisional) is GroupPlacementDecision.Rejected
        ) {
            correctedGroups++
            item.copy(groupId = null)
        } else item
    }

    val usedRelationIds = sourceRelations.mapTo(linkedSetOf()) { it.id }
    val seenRelationIds = mutableMapOf<String, Int>()
    val normalizedRelations = sourceRelations.map { relation ->
        val occurrence = (seenRelationIds[relation.id] ?: 0) + 1
        seenRelationIds[relation.id] = occurrence
        val relationId = when {
            relation.id.isBlank() -> stableDuplicateId("relation", occurrence, usedRelationIds)
            occurrence == 1 -> relation.id
            else -> stableDuplicateId(relation.id, occurrence, usedRelationIds)
        }
        relation.copy(
            id = relationId,
            fromItemId = canonicalIds[relation.fromItemId] ?: relation.fromItemId,
            toItemId = canonicalIds[relation.toItemId] ?: relation.toItemId,
        )
    }
    val safeRelations = OrderingPolicy.sanitizeRelations(normalizedItems, normalizedRelations)
    val duplicateRelationIds = sourceRelations.size - seenRelationIds.size
    return BackupNormalizationResult(
        items = normalizedItems,
        relations = safeRelations,
        duplicateItemIds = duplicateItemIds,
        duplicateRelationIds = duplicateRelationIds,
        correctedGroupReferences = correctedGroups,
        correctedParentReferences = correctedParents,
        correctedConversionReferences = correctedConversions,
        correctedRelations = normalizedRelations.size - safeRelations.size,
    )
}

/** Structural checks performed before an item enters the normalization pass. */
internal fun isBackupItemImportable(item: AppItem): Boolean =
    item.id.isNotBlank() && item.title.isNotBlank() && item.title.length <= 200 && item.body.length <= 100_000

private fun stableDuplicateId(base: String, occurrence: Int, used: MutableSet<String>): String {
    var suffix = occurrence
    var candidate = "$base~duplicate-$suffix"
    while (!used.add(candidate)) {
        suffix++
        candidate = "$base~duplicate-$suffix"
    }
    return candidate
}
