-- ============================================================================
-- TESTE 3: BANCO DE DADOS E ANÁLISE
-- Compatibilidade: PostgreSQL > 10.0
-- ============================================================================

-- 1. LIMPEZA INICIAL
DROP TABLE IF EXISTS despesas_agregadas;
DROP TABLE IF EXISTS despesas;
DROP TABLE IF EXISTS operadoras;
DROP TABLE IF EXISTS temp_importacao;

-- ============================================================================
-- 3.2. DDL - CRIAÇÃO DAS TABELAS (Estrutura)
-- ============================================================================

-- Justificativa de Normalização (Opção B):
-- Optou-se por separar 'operadoras' de 'despesas' para evitar redundância de strings
-- (Razão Social repetida milhões de vezes) e garantir integridade referencial.
-- O volume esperado é médio, e atualizações cadastrais são menos frequentes que lançamentos.

-- Tabela 1: Dados Cadastrais (Normalizada)
CREATE TABLE operadoras (
    registro_ans VARCHAR(20) PRIMARY KEY,
    cnpj VARCHAR(20),
    razao_social TEXT,
    uf VARCHAR(2)
);

-- Tabela 2: Dados Consolidados de Despesas (Normalizada)
-- Tipos de Dados:
-- Valor: NUMERIC(15,2) para evitar erros de arredondamento de ponto flutuante (FLOAT) em dados financeiros.
-- Datas: INT para Ano/Trimestre facilita indexação e performance em queries de agregação.
CREATE TABLE despesas (
    id SERIAL PRIMARY KEY,
    registro_ans VARCHAR(20) REFERENCES operadoras(registro_ans),
    trimestre INT,
    ano INT,
    valor NUMERIC(15,2)
);

-- Tabela 3: Dados Agregados (Solicitado no item 3.2)
-- Esta tabela armazena o resultado pré-processado do teste 2.3
CREATE TABLE despesas_agregadas (
    razao_social TEXT,
    uf VARCHAR(2),
    total_despesas NUMERIC(15,2),
    media_trimestral NUMERIC(15,2),
    desvio_padrao NUMERIC(15,2)
);

-- Tabela Temporária (Staging)
-- Usada para tratar inconsistências (ex: limpar formatação do CNPJ) antes da carga final.
CREATE TABLE temp_importacao (
    registro_ans VARCHAR(20),
    cnpj VARCHAR(50),
    razao_social TEXT,
    trimestre INT,
    ano INT,
    uf VARCHAR(5),
    valor_despesas NUMERIC(15,2)
);

-- ============================================================================
-- 3.3. IMPORTAÇÃO DOS DADOS (ETL via SQL)
-- ============================================================================

-- IMPORTANTE: Atualize o caminho abaixo para o seu arquivo Resultado_Final.csv
COPY temp_importacao(registro_ans, cnpj, razao_social, trimestre, ano, uf, valor_despesas)
FROM 'C:/programacao/vaga.estagio.intuitivecare/Resultado_Final.csv'
WITH (FORMAT CSV, HEADER true, DELIMITER ';', ENCODING 'UTF8');

-- IMPORTANTE: Atualize o caminho para despesas_agregadas.csv
COPY despesas_agregadas(razao_social, uf, total_despesas, media_trimestral, desvio_padrao)
FROM 'C:/programacao/vaga.estagio.intuitivecare/despesas_agregadas.csv'
WITH (FORMAT CSV, HEADER true, DELIMITER ';', ENCODING 'UTF8');

-- TRATAMENTO E CARGA (Normalização)

-- 1. Inserir Operadoras (Tratando duplicatas com ON CONFLICT)
INSERT INTO operadoras (registro_ans, cnpj, razao_social, uf)
SELECT DISTINCT
    registro_ans,
    REPLACE(cnpj, '''', '') as cnpj_limpo, -- Remove apóstrofo de formatação Excel
    razao_social,
    uf
FROM temp_importacao
ON CONFLICT (registro_ans) DO UPDATE
SET razao_social = EXCLUDED.razao_social; -- Atualiza nome se mudou

-- 2. Inserir Despesas
INSERT INTO despesas (registro_ans, trimestre, ano, valor)
SELECT registro_ans, trimestre, ano, valor_despesas
FROM temp_importacao;

-- Limpeza da Staging
TRUNCATE TABLE temp_importacao;

-- ============================================================================
-- 3.4. QUERIES ANALÍTICAS
-- ============================================================================

-- QUERY 1: Maior Crescimento Percentual (Último Tri vs Primeiro Tri)
-- Desafio: Operadoras sem dados no primeiro trimestre são ignoradas (Join interno),
-- pois matematicamente não se calcula crescimento sobre zero/nulo.
WITH periodo_inicial AS (
    SELECT registro_ans, SUM(valor) as total_ini
    FROM despesas WHERE ano = 2024 AND trimestre = 4 GROUP BY registro_ans
),
periodo_final AS (
    SELECT registro_ans, SUM(valor) as total_fim
    FROM despesas WHERE ano = 2025 AND trimestre = 3 GROUP BY registro_ans
)
SELECT
    o.razao_social,
    pi.total_ini as inicio,
    pf.total_fim as fim,
    ROUND(((pf.total_fim - pi.total_ini) / pi.total_ini) * 100, 2) as crescimento_pct
FROM periodo_inicial pi
JOIN periodo_final pf ON pi.registro_ans = pf.registro_ans
JOIN operadoras o ON pi.registro_ans = o.registro_ans
WHERE pi.total_ini > 0 -- Evita divisão por zero
ORDER BY crescimento_pct DESC
LIMIT 5;


-- QUERY 2: Distribuição por UF (Top 5) + Média por Operadora
-- Desafio Adicional: Calcular média de gastos POR OPERADORA naquele estado.
SELECT
    o.uf,
    SUM(d.valor) as total_despesas_estado,
    COUNT(DISTINCT d.registro_ans) as qtd_operadoras,
    ROUND(SUM(d.valor) / COUNT(DISTINCT d.registro_ans), 2) as media_por_operadora
FROM despesas d
JOIN operadoras o ON d.registro_ans = o.registro_ans
GROUP BY o.uf
ORDER BY total_despesas_estado DESC
LIMIT 5;


-- QUERY 3: Operadoras acima da média em pelo menos 2 trimestres
-- Trade-off: Utilizou-se CTEs e Window Functions para legibilidade.
-- A performance é otimizada pois o banco calcula a média uma única vez por partição.
WITH medias_trimestrais AS (
    SELECT ano, trimestre, AVG(valor) as media_geral
    FROM despesas
    GROUP BY ano, trimestre
),
performance_operadora AS (
    SELECT
        d.registro_ans,
        d.ano,
        d.trimestre,
        SUM(d.valor) as gasto_operadora,
        m.media_geral
    FROM despesas d
    JOIN medias_trimestrais m ON d.ano = m.ano AND d.trimestre = m.trimestre
    GROUP BY d.registro_ans, d.ano, d.trimestre, m.media_geral
)
SELECT
    o.razao_social,
    COUNT(*) as trimestres_acima_media
FROM performance_operadora p
JOIN operadoras o ON p.registro_ans = o.registro_ans
WHERE p.gasto_operadora > p.media_geral
GROUP BY o.registro_ans, o.razao_social
HAVING COUNT(*) >= 2 -- Filtro: Pelo menos 2 trimestres
ORDER BY trimestres_acima_media DESC, o.razao_social
LIMIT 10;