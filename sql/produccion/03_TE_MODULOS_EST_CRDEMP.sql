-- Módulo de escritorio CredencialesSM (no menú web: ESWEB = 0).
-- Clave: EST_CRDEMP. Vigencia por FECVIG / FECVEN (ajustar fechas antes de producción).
-- Padre 0: independiente del menú CREDENCIALIZACION web (puede estar vencido).

INSERT INTO TE_MODULOS (
    CVE_MODULO, CVE_MODPADRE, DESC_MOD, ACTION, CONTEXTO,
    FECVIG, FECVEN, MANUAL, ORDEN, IP, PUERTO, HRINICIO, HRFIN, ESWEB
)
SELECT
    'EST_CRDEMP',
    '0',
    'Estación credenciales empleados',
    '#',
    'SMSEM',
    TO_DATE('01/01/2026', 'DD/MM/YYYY'),
    TO_DATE('31/12/2027', 'DD/MM/YYYY'),
    NULL,
    90,
    NULL,
    NULL,
    '00:00',
    '23:59',
    '0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM TE_MODULOS WHERE TRIM(CVE_MODULO) = 'EST_CRDEMP'
);

-- Si ya existe, alinear vigencia / descripción (no pisa FECVEN si solo se reejecuta el INSERT).
UPDATE TE_MODULOS
SET DESC_MOD = 'Estación credenciales empleados',
    CVE_MODPADRE = '0',
    ACTION = '#',
    CONTEXTO = 'SMSEM',
    HRINICIO = '00:00',
    HRFIN = '23:59',
    ESWEB = '0',
    ORDEN = 90,
    FECVIG = NVL(FECVIG, TO_DATE('01/01/2026', 'DD/MM/YYYY')),
    FECVEN = NVL(FECVEN, TO_DATE('31/12/2027', 'DD/MM/YYYY'))
WHERE TRIM(CVE_MODULO) = 'EST_CRDEMP';

COMMIT;
