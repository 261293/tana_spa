const CREATABLE_BY: Record<string, readonly string[]> = {
  admin: ["admin", "owner", "staff", "customer"],
  owner: ["staff", "customer"],
};

const MANAGES_ACCOUNTS: readonly string[] = ["admin", "owner"];

export function canCreate(actor: string, target: string): boolean {
  return Object.hasOwn(CREATABLE_BY, actor) && CREATABLE_BY[actor].includes(target);
}

export function hasManageAccounts(role: string): boolean {
  return MANAGES_ACCOUNTS.includes(role);
}
