import unittest

from nbtlib import Byte, Compound, Int, List, Short, String

from migrate import migrate_item, migrate_items_anywhere


class SweepingEdgeMigrationTest(unittest.TestCase):
    def test_legacy_enchantments_keep_levels(self):
        for tag_key in ('Enchantments', 'StoredEnchantments'):
            with self.subTest(tag_key=tag_key):
                item = Compound({'id': String('minecraft:enchanted_book'), 'Count': Byte(1),
                                 'tag': Compound({tag_key: List[Compound]([
                                     Compound({'id': String('minecraft:sweeping'), 'lvl': Short(3)}),
                                     Compound({'id': String('minecraft:sharpness'), 'lvl': Short(5)}),
                                 ])})})
                migrated = migrate_item(item)
                key = 'minecraft:stored_enchantments' if tag_key == 'StoredEnchantments' else 'minecraft:enchantments'
                self.assertEqual(migrated['components'][key]['levels'],
                                 Compound({'minecraft:sweeping_edge': Int(3), 'minecraft:sharpness': Int(5)}))
                self.assertEqual(migrate_item(migrated), migrated)

    def test_component_enchantments_in_bookshelf_are_idempotent(self):
        for key in ('minecraft:enchantments', 'minecraft:stored_enchantments'):
            with self.subTest(key=key):
                item = Compound({'id': String('minecraft:enchanted_book'), 'count': Int(1), 'Slot': Byte(4),
                                 'components': Compound({key: Compound({'levels': Compound({
                                     'minecraft:sweeping': Int(3), 'minecraft:sharpness': Int(5),
                                 })})})})
                root = Compound({'Items': List[Compound]([item])})
                migrate_items_anywhere(root)
                expected = Compound({'minecraft:sweeping_edge': Int(3), 'minecraft:sharpness': Int(5)})
                self.assertEqual(root['Items'][0]['components'][key]['levels'], expected)
                self.assertEqual(root['Items'][0]['Slot'], Byte(4))
                first = root.snbt()
                migrate_items_anywhere(root)
                self.assertEqual(root.snbt(), first)

    def test_existing_component_keeps_higher_level(self):
        item = Compound({'id': String('minecraft:enchanted_book'), 'count': Int(1),
                         'components': Compound({'minecraft:stored_enchantments': Compound({'levels': Compound({
                             'minecraft:sweeping': Int(2), 'minecraft:sweeping_edge': Int(3),
                         })})})})
        migrated = migrate_item(item)
        self.assertEqual(migrated['components']['minecraft:stored_enchantments']['levels'],
                         Compound({'minecraft:sweeping_edge': Int(3)}))


if __name__ == '__main__':
    unittest.main()
