# Icons

Line icons in `app/src/main/res-packs/drawable/*_itemicon.xml` (groups meal, vehicle, travel, home,
health, care, shop, digital, leisure, money, people, work, misc) and `app/src/main/res/drawable/ic_tabler_*.xml`
are [Tabler Icons](https://github.com/tabler/tabler-icons) by Paweł Kuna, MIT License
(see `LICENSE-tabler-icons.txt`), converted to Android VectorDrawables by `import_tabler.py`.
Brand icons depict trademarks of their owners and are used only to label the user's own categories.

Regenerate after editing the lists in the script:

    python tools/icons/import_tabler.py --self-test
    python tools/icons/import_tabler.py --check
    python tools/icons/import_tabler.py
