-- One-off: assign Tabler line icons to categories imported from 1Money, matched by title.
-- Usage (psql against the self-hosted Supabase Postgres):
--   psql "$DATABASE_URL" -v user_id="'<auth.users.id uuid>'" -f tools/icons/assign_category_icons.sql
-- Re-runnable; only rows whose title matches are changed. Icon names: tools/icons/generated_item_icon_names.txt

begin;

create temporary table category_icon_map (title text primary key, icon text not null) on commit drop;

insert into category_icon_map (title, icon) values
    ('продукты', 'meal_shopping_cart'),
    ('еда', 'meal_tools_kitchen_2'),
    ('кафе', 'meal_coffee'),
    ('мак', 'meal_burger'),
    ('bolt food', 'meal_paper_bag'),
    ('алко', 'meal_beer'),
    ('отдых', 'travel_beach'),
    ('путешествия', 'travel_luggage'),
    ('транспорт', 'vehicle_bus'),
    ('общественный', 'vehicle_train'),
    ('такси', 'vehicle_car_suv'),
    ('машина', 'vehicle_car'),
    ('газ/бензин', 'vehicle_gas_station'),
    ('самокат', 'vehicle_scooter_electric'),
    ('мотоцикл', 'vehicle_motorbike'),
    ('парковка', 'vehicle_parking'),
    ('здоровье', 'health_heartbeat'),
    ('аптека', 'health_pill'),
    ('врач', 'health_stethoscope'),
    ('стоматолог', 'health_dental'),
    ('спорт', 'health_barbell'),
    ('уход за собой', 'care_bath'),
    ('стрижка', 'care_scissors'),
    ('подарки', 'shop_gift'),
    ('подарок', 'shop_gift'),
    ('покупки', 'shop_shopping_bag'),
    ('одежда', 'shop_shirt'),
    ('техника', 'shop_device_laptop'),
    ('продажа вещей', 'shop_tag'),
    ('квартира', 'home_home'),
    ('аренда', 'home_key'),
    ('ремонт', 'home_tool'),
    ('мебель', 'home_sofa'),
    ('вода', 'home_droplet'),
    ('электричество', 'home_bolt'),
    ('газ', 'home_flame'),
    ('интернет балтиком', 'home_wifi'),
    ('переезд', 'home_truck_delivery'),
    ('сервисы', 'digital_cloud'),
    ('подписки', 'digital_world_www'),
    ('ютуб', 'digital_brand_youtube'),
    ('aws', 'digital_brand_aws'),
    ('azure', 'digital_brand_azure'),
    ('спотифай', 'digital_brand_spotify'),
    ('netflix', 'digital_brand_netflix'),
    ('игры', 'leisure_device_gamepad_2'),
    ('кино', 'leisure_movie'),
    ('книги', 'leisure_book'),
    ('музыка', 'leisure_music'),
    ('вейп', 'leisure_smoking'),
    ('долги', 'money_scale'),
    ('крипта', 'money_currency_bitcoin'),
    ('кэшбэк', 'money_receipt_refund'),
    ('проценты', 'money_percentage'),
    ('налоги', 'money_receipt_tax'),
    ('банк', 'money_building_bank'),
    ('кредит', 'money_credit_card'),
    ('перевод', 'money_arrows_exchange'),
    ('зарплата', 'work_briefcase'),
    ('командировка', 'work_plane_departure'),
    ('стипендия', 'people_school'),
    ('образование', 'people_school'),
    ('животные', 'people_paw'),
    ('дети', 'people_baby_carriage'),
    ('телефон', 'misc_phone'),
    ('связь', 'misc_phone'),
    ('страховка', 'misc_shield'),
    ('другое', 'misc_dots');

update money.categories c
set icon = m.icon
from category_icon_map m
where c.user_id = :user_id
  and lower(btrim(c.title)) = m.title;

-- Report: categories still without an icon (assign them in the app's icon picker).
select c.title, case when c.parent_category_id is null then 'category' else 'subcategory' end as kind
from money.categories c
where c.user_id = :user_id
  and c.icon is null
order by kind, c.title;

commit;
