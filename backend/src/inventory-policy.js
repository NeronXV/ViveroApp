import { ApiError } from './catalog.js';
import { requireCapabilities } from './auth/service.js';

// OWNER is the authoritative role returned by the existing identity service.
// This narrows the existing inventory privilege; it grants no capabilities.
export function requireInventoryOwner(context) {
  requireCapabilities(context, ['MANAGE_INVENTORY'], context.branch?.id);
  if (context.role?.name !== 'OWNER' || !context.branch?.is_active) {
    throw new ApiError(403, 'INVENTORY_OWNER_REQUIRED');
  }
}

export function inventoryPermissions(context) {
  const write = context.access_state === 'ACTIVE' && Boolean(context.branch?.is_active)
    && context.capabilities.includes('MANAGE_INVENTORY');
  const owner = write && context.role?.name === 'OWNER';
  return { can_record_count: write, can_receive: owner, can_approve_counts: owner };
}
