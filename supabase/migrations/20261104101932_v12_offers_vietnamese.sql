-- =====================================================================
-- V12: the offers in Vietnamese.
--
-- V11 translated the treatments and the therapists but not the vouchers,
-- so a guest reading the app in Vietnamese still met "20% off any facial"
-- on the offers screen and "10% off your first booking" on the booking
-- review. Same shape as V11: the existing columns stay the English text,
-- the _vi columns carry the Vietnamese, NULL falls back to the base.
--
-- The money inside the prose is written in dong because the app shows dong
-- to Vietnamese readers, converted at the VND_PER_USD the app itself uses.
-- A code the owner adds later has no translation and is shown as authored.
-- =====================================================================

ALTER TABLE vouchers
    ADD COLUMN title_vi       text,
    ADD COLUMN description_vi text;

UPDATE vouchers SET title_vi = 'Giảm 20% cho mọi liệu trình chăm sóc da mặt',
                    description_vi = 'Chỉ áp dụng cho chăm sóc da mặt. Mỗi khách một lần.'
 WHERE code = 'JADE20';

UPDATE vouchers SET title_vi = 'Giảm 375.000đ cho massage ngày thường',
                    description_vi = 'Áp dụng thứ Ba đến thứ Năm. Đơn tối thiểu 3.000.000đ.'
 WHERE code = 'MIDWEEK15';

UPDATE vouchers SET title_vi = 'Giảm 10% cho lần đặt đầu tiên',
                    description_vi = 'Dành cho khách mới. Mọi liệu trình.'
 WHERE code = 'FIRSTVISIT';
