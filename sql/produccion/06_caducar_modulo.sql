-- Caducar o ampliar la vigencia del módulo de escritorio EST_CRDEMP.

-- Caducar ya (deja de entrar la estación):
-- UPDATE TE_MODULOS
-- SET FECVEN = TRUNC(SYSDATE) - 1
-- WHERE TRIM(CVE_MODULO) = 'EST_CRDEMP';

-- Ampliar vigencia hasta fin de 2028:
UPDATE TE_MODULOS
SET FECVEN = TO_DATE('31/12/2028', 'DD/MM/YYYY')
WHERE TRIM(CVE_MODULO) = 'EST_CRDEMP';

COMMIT;
