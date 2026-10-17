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

export function requireInventoryReception(context) {
  requireCapabilities(context, ['MANAGE_INVENTORY'], context.branch?.id);
  if (!['OWNER', 'ADMIN', 'MANAGER'].includes(context.role?.name) || !context.branch?.is_active) {
    throw new ApiError(403, 'INVENTORY_RECEPTION_FORBIDDEN');
  }
}

export function inventoryPermissions(context) {
  const write = context.access_state === 'ACTIVE' && Boolean(context.branch?.is_active)
    && context.capabilities.includes('MANAGE_INVENTORY');
  const roleName = context.role?.name;
  const owner = write && roleName === 'OWNER';
  const can_receive = write && ['OWNER', 'ADMIN', 'MANAGER'].includes(roleName);
  return { can_record_count: write, can_receive, can_approve_counts: owner };
}
