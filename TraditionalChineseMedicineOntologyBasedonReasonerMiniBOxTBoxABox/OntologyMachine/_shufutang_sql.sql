USE jingfangdb;
INSERT IGNORE INTO fangji (iri, label) VALUES ('Shufutang','术附汤');
INSERT IGNORE INTO fangji_yaowu (fangji_id, yaowu_id)
SELECT f.id, y.id FROM fangji f, yaowu y
WHERE f.iri='Shufutang' AND y.iri IN ('Baizhu','Fuzi','Gancao');
SELECT f.iri, f.label, GROUP_CONCAT(y.iri) AS herbs
FROM fangji f LEFT JOIN fangji_yaowu fy ON fy.fangji_id=f.id
LEFT JOIN yaowu y ON y.id=fy.yaowu_id
WHERE f.iri='Shufutang' GROUP BY f.id;
