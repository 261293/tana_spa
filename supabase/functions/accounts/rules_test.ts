import { assertEquals } from "jsr:@std/assert@1.0.13";
import { canCreate, hasManageAccounts } from "./rules.ts";

const ROLES = ["admin", "owner", "staff", "customer"] as const;

Deno.test("can_create_matches_spa_roles", () => {
  const creatable: Record<string, string[]> = {
    admin: ["admin", "owner", "staff", "customer"],
    owner: ["staff", "customer"],
    staff: [],
    customer: [],
  };
  for (const actor of ROLES) {
    for (const target of ROLES) {
      assertEquals(canCreate(actor, target), creatable[actor].includes(target), `${actor} -> ${target}`);
    }
  }
});

Deno.test("unknown_roles_create_nothing_and_are_never_created", () => {
  for (const role of ROLES) {
    assertEquals(canCreate("superuser", role), false);
    assertEquals(canCreate(role, "superuser"), false);
    assertEquals(canCreate(role, "ADMIN"), false);
    assertEquals(canCreate(role, "toString"), false);
  }
});

Deno.test("manage_accounts_belongs_to_owner_and_admin", () => {
  assertEquals(ROLES.map((role) => [role, hasManageAccounts(role)]), [
    ["admin", true],
    ["owner", true],
    ["staff", false],
    ["customer", false],
  ]);
  assertEquals(hasManageAccounts("constructor"), false);
});
