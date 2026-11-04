-- =====================================================================
-- V11: the menu in Vietnamese.
--
-- The app reads in Vietnamese and falls back to English, but the treatments
-- are data rather than resources, so until now every guest saw the menu in
-- English whatever language the app was set to.
--
-- The existing columns stay the English text and the _vi columns carry the
-- Vietnamese. NULL means "nothing written in that language" and the reader
-- falls back to the base column, so a treatment the owner adds in one
-- language is still shown to everybody rather than disappearing.
-- =====================================================================

ALTER TABLE services
    ADD COLUMN name_vi        text,
    ADD COLUMN description_vi text;

ALTER TABLE therapists
    ADD COLUMN focus_vi text;

UPDATE services SET name_vi = 'Massage đá nóng',
                    description_vi = 'Đá bazan, day ấn toàn thân, massage da đầu'
 WHERE slug = 'warm-stone-massage';

UPDATE services SET name_vi = 'Chăm sóc da mặt con lăn ngọc',
                    description_vi = 'Làm sạch hai bước, mặt nạ enzyme, lăn đá ngọc mát'
 WHERE slug = 'jade-roller-facial';

UPDATE services SET name_vi = 'Massage sâu',
                    description_vi = 'Giải tỏa vùng lưng, cổ và vai'
 WHERE slug = 'deep-tissue-massage';

UPDATE services SET name_vi = 'Tẩy da chết matcha',
                    description_vi = 'Tẩy muối trà xanh, tắm lại, dưỡng ẩm'
 WHERE slug = 'matcha-body-polish';

UPDATE services SET name_vi = 'Nghi thức chườm thảo dược',
                    description_vi = 'Túi chườm sả hấp nóng, massage dầu nhẹ'
 WHERE slug = 'herbal-compress-ritual';

-- A therapist's name is their name in either language; only what they are
-- known for is written twice.
UPDATE therapists SET focus_vi = 'Đá nóng & massage sâu'      WHERE slug = 'mai';
UPDATE therapists SET focus_vi = 'Chăm sóc da mặt & làn da'   WHERE slug = 'lena';
UPDATE therapists SET focus_vi = 'Thể thao & phục hồi'        WHERE slug = 'tomas';
