# ==============================================================================
# ARQUIVO: main.py
# OBJETIVO: Criar uma API simples para ler o CSV gerado pelo Java e servir pro site.
# AUTOR: [Seu Nome]
# ==============================================================================

# Importando as ferramentas de trabalho:
from fastapi import FastAPI, HTTPException # FastAPI é o servidor web ultra-rápido
from fastapi.middleware.cors import CORSMiddleware # CORS é pra liberar o acesso do navegador
import pandas as pd # Pandas é o "Excel do Python", serve pra mexer nos dados
import os # Pra mexer com arquivos do sistema operacional

# Cria a aplicação web
app = FastAPI()

# --- CONFIGURAÇÃO DE SEGURANÇA (CORS) ---
# Isso aqui é essencial! Sem isso, quando o index.html tentar acessar a API,
# o navegador bloqueia dizendo que é "perigoso". Aqui liberamos tudo.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], # Libera pra qualquer um (na vida real, seria só o domínio do site)
    allow_methods=["*"], # Libera GET, POST, etc
    allow_headers=["*"],
)

# Caminho do arquivo que o Java gerou. Tem que estar na mesma pasta!
CSV_PATH = "Resultado_Final.csv"

# --- FUNÇÃO AUXILIAR: CARREGAR DADOS ---
# A gente cria essa função pra não ficar repetindo código de leitura toda hora.
def carregar_dados():
    # 1. Segurança: Verifica se o Java já rodou e criou o arquivo
    if not os.path.exists(CSV_PATH):
        raise HTTPException(status_code=500, detail="Arquivo CSV não encontrado. Rode o código Java primeiro, chefe!")

    # 2. Leitura: Tenta ler o CSV.
    # Como o Java grava em UTF-8 com BOM, o Python costuma entender 'utf-8'.
    # Mas se der ruim, tentamos 'latin1' (plano B).
    try:
        # dtype=str garante que o CNPJ não perca o zero à esquerda
        df = pd.read_csv(CSV_PATH, sep=";", encoding="utf-8", dtype=str)
    except:
        df = pd.read_csv(CSV_PATH, sep=";", encoding="latin1", dtype=str)

    # 3. Limpeza: Troca valores vazios (NaN) por texto vazio pra não quebrar o JSON
    df = df.fillna("")

    # 4. Ajuste Fino: Lembra que no Java colocamos um apóstrofo (') pro Excel?
    # Aqui a gente tira ele, senão fica feio na tela do site.
    if 'CNPJ' in df.columns:
        df['CNPJ'] = df['CNPJ'].str.replace("'", "")

    return df

# --- ROTA 1: PÁGINA INICIAL ---
# Só pra saber se a API tá viva.
@app.get("/")
def home():
    return {"status": "API Online e Rodando! 🚀", "mensagem": "Use as rotas /api/operadoras ou /api/estatisticas"}

# --- ROTA 2: LISTAGEM E BUSCA (TABELA) ---
@app.get("/api/operadoras")
def listar_operadoras(busca: str = None, page: int = 1, limit: int = 10):
    # Carrega a tabela na memória
    df = carregar_dados()

    # Lógica de Busca (Case Insensitive - tanto faz maiúscula ou minúscula)
    if busca:
        busca = busca.lower()
        # Procura no Nome OU no CNPJ
        mascara = df['RazaoSocial'].str.lower().str.contains(busca) | df['CNPJ'].str.contains(busca)
        df = df[mascara]

    # Paginação manual (corta o dataframe igual fatiar bolo)
    total_registros = len(df)
    inicio = (page - 1) * limit
    final = inicio + limit

    # Transforma em JSON (lista de dicionários) pro JavaScript entender
    dados_pagina = df.iloc[inicio:final].to_dict(orient="records")

    return {
        "data": dados_pagina,
        "total": total_registros,
        "page": page,
        "limit": limit
    }

# --- ROTA 3: DADOS PRO GRÁFICO ---
@app.get("/api/estatisticas")
def obter_dados_grafico():
    df = carregar_dados()

    # O valor vem como texto (ex: "1.500,00"). O gráfico precisa de número (1500.00).
    # 1. Removemos o ponto de milhar
    # 2. Trocamos a vírgula decimal por ponto (padrão americano/python)
    # 3. Convertemos pra float
    df['ValorDespesas'] = pd.to_numeric(
        df['ValorDespesas'].str.replace(".", "", regex=False).str.replace(",", ".", regex=False),
        errors='coerce' # Se der erro na conversão, vira 0
    ).fillna(0)

    # Agrupa por UF, soma tudo e pega os Top 5 estados mais "gastões"
    top_estados = df.groupby('UF')['ValorDespesas'].sum().sort_values(ascending=False).head(5)

    return {
        "labels": top_estados.index.tolist(), # Ex: ["SP", "RJ", "MG"]
        "values": top_estados.values.tolist() # Ex: [50000.0, 20000.0, ...]
    }

# Dica pro Dev: Pra rodar isso, abra o terminal e digite:
# python -m uvicorn main:app --reload