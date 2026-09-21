import importlib.util
from pathlib import Path
import unittest

import nbtlib

MODULE_PATH = Path(__file__).with_name("repair_entity_nbt.py")
SPEC = importlib.util.spec_from_file_location("repair_entity_nbt", MODULE_PATH)
repair = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(repair)


def polluted_stack(count=93703):
    enchantments = nbtlib.List[nbtlib.Compound]([
        nbtlib.Compound({"id": nbtlib.String("minecraft:protection"), "lvl": nbtlib.Short(3)})
        for _ in range(count)
    ])
    return nbtlib.Compound({
        "id": nbtlib.String("minecraft:air"),
        "Count": nbtlib.Byte(0),
        "tag": nbtlib.Compound({
            "GunDisplayId": nbtlib.String("sfms:lvsa_c_erode_1_display"),
            "GunCurrentAmmoCount": nbtlib.Int(1),
            "Enchantments": enchantments,
        }),
    })


class RepairEntityNbtTest(unittest.TestCase):
    def test_repairs_marker_and_polluted_empty_stack(self):
        entity = nbtlib.Compound({
            "id": nbtlib.String("minecraft:zombie"),
            "ActiveEffects": nbtlib.List[nbtlib.Compound]([
                nbtlib.Compound({
                    "forge:id": nbtlib.String("spore:marker"),
                    "CurativeItems": nbtlib.List[nbtlib.Compound]([polluted_stack()]),
                })
            ]),
        })
        before = repair.serialized_size(entity)
        changes = repair.sanitize_entity(entity)
        after = repair.serialized_size(entity)
        self.assertEqual(1, len(changes))
        self.assertEqual("marker_curative_items", changes[0]["kind"])
        self.assertEqual(0, len(entity["ActiveEffects"][0]["CurativeItems"]))
        self.assertLess(after, 1024)
        self.assertGreater(before, 3_000_000)

    def test_repairs_polluted_cnpc_weapon(self):
        entity = nbtlib.Compound({
            "id": nbtlib.String("customnpcs:customnpc"),
            "Weapons": nbtlib.List[nbtlib.Compound]([polluted_stack()]),
        })
        changes = repair.sanitize_entity(entity)
        self.assertEqual("corrupt_empty_tacz_stack", changes[0]["kind"])
        self.assertEqual("minecraft:air", str(entity["Weapons"][0]["id"]))
        self.assertNotIn("tag", entity["Weapons"][0])

    def test_preserves_legitimate_enchanted_item(self):
        sword = nbtlib.Compound({
            "id": nbtlib.String("minecraft:diamond_sword"),
            "Count": nbtlib.Byte(1),
            "tag": nbtlib.Compound({
                "Enchantments": nbtlib.List[nbtlib.Compound]([
                    nbtlib.Compound({"id": nbtlib.String("minecraft:sharpness"), "lvl": nbtlib.Short(5)})
                ])
            }),
        })
        entity = nbtlib.Compound({"id": nbtlib.String("minecraft:zombie"), "HandItems": nbtlib.List[nbtlib.Compound]([sword])})
        before = repair.nbt_bytes(nbtlib.File(entity))
        self.assertEqual([], repair.sanitize_entity(entity))
        self.assertEqual(before, repair.nbt_bytes(nbtlib.File(entity)))


if __name__ == "__main__":
    unittest.main()
