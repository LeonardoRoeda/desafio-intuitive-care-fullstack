# 🚀 Desafio Técnico - Intuitive Care

Desafio Fullstack.
O projeto foi desenvolvido com foco nos pilares solicitados: **KISS (Simplicidade)**.
---

## 🛠️ Tecnologias Utilizadas
* **IDE:** IntelliJ
* **ETL:** Java 11+ (Processamento de Arquivos)
* **Backend:** Python 3.9+ (FastAPI + Pandas)
* **Frontend:** Vue.js 3 (CDN) + TailwindCSS
* **Banco de Dados:** PostgreSQL

---

## 📋 Como Executar o Projeto

### 1. Processamento de Dados (Java)
O Java é responsável por baixar, limpar e consolidar os dados.
1.  Na raiz, execute a classe `TesteEstagio.java`.
2.  **Saída:** Serão gerados os arquivos `Resultado_Final.csv`, `despesas_agregadas.csv` e o zip `Teste_Intuitive_Entrega.zip`.

### 2. Banco de Dados (SQL)
1.  Vá até a pasta `/sql` e abra o script `dados.sql`.
2.  Atualize o caminho do comando `COPY` para apontar para o `Resultado_Final.csv` gerado acima.
3.  Execute no seu banco PostgreSQL.

### 3. API e Dashboard (Python & Vue)
1.  Instale as dependências: `pip install fastapi "uvicorn[standard]" pandas`
2.  Rode a API: `python -m uvicorn main:app --reload` dentro da pasta do projeto
3.  Abra o arquivo `index.html` no navegador.

---

## 🛡️ Documentação Trade-offs

Esta seção documenta as escolhas arquiteturais e o tratamento de inconsistências solicitados nas instruções do teste.

### 📍 1. Tratamento de Inconsistências (Item 1.3 do Teste)

Durante a consolidação dos dados no Java, as seguintes anomalias foram tratadas:

* **A. CNPJs Duplicados com Razões Sociais Diferentes**
    * **Cenário:** Uma operadora mudou de nome entre o 1º e o 3º trimestre. O CNPJ é o mesmo, mas a Razão Social divergiu.
    * **Abordagem:** **Unificação via Fonte da Verdade (Cadastro).**
    * **Justificativa:** Utilizei o arquivo `Relatorio_cadop.csv` (Cadastro de Operadoras Ativas) como mestre. Ao processar as despesas, ignorei a Razão Social que vinha no arquivo contábil antigo e forcei o uso do nome atualizado do cadastro. Isso garante unicidade e integridade dos dados cadastrais.

* **B. Valores Zerados ou Negativos**
    * **Cenário:** Lançamentos contábeis com valor `0.00` ou negativos (ex: `-1500.00`).
    * **Abordagem:** **Preservação Integral.**
    * **Justificativa:** Em contabilidade, valores negativos representam estornos, ajustes ou provisões revertidas. Excluí-los invalidaria o balanço final da operadora. Optei por manter os dados originais, aplicando apenas normalização de formatação (troca de vírgula por ponto).

* **C. Trimestres com Formatos de Data Inconsistentes**
    * **Cenário:** O conteúdo interno dos CSVs da ANS nem sempre possui colunas de data padronizadas.
    * **Abordagem:** **Inferência por Metadados (Nome do Arquivo).**
    * **Justificativa:** Como os arquivos são baixados de URLs padronizadas (`2024/3T`), extraí o Ano e o Trimestre diretamente do nome do arquivo (ex: `2025_3T.csv`) via código Java. Isso é mais confiável do que tentar "adivinhar" qual coluna dentro do CSV representa a data.

---

### 📍 2. Estratégias de Processamento (Itens 1.2 e 2.2)

* **Processamento de Arquivos (Memória vs. Stream):**
    * **Decisão:** Abordagem Híbrida.
    * **Justificativa:** O arquivo de cadastro (~30MB) foi carregado em memória (`HashMap`) para garantir acesso O(1) rápido durante o cruzamento. Já os arquivos de despesas (que podem crescer indefinidamente) foram lidos linha a linha (`BufferedReader`), evitando estouro de memória (Out of Memory) caso o volume de dados aumente no futuro.

* **Join de Dados (CNPJs sem Match):**
    * **Decisão:** Left Join (Prioridade para Despesas).
    * **Justificativa:** Se uma operadora tem despesas mas não está no cadastro de "Ativas", ela ainda gerou custo histórico. Esses registros foram mantidos com a Razão Social marcada como "Operadora Desconhecida" ou "Cadastro Inativo", garantindo que a soma financeira total bata com os arquivos originais.

---

### 📍 3. Banco de Dados (Item 3.2)

* **Normalização (Opção B):**
    * Separei em duas tabelas: `operadoras` (Dados Cadastrais) e `despesas` (Dados Financeiros).
    * **Justificativa:** Evita redundância. Se tivéssemos uma tabela única, a string da "Razão Social" seria repetida milhões de vezes, desperdiçando armazenamento e dificultando updates de nome.

* **Tipos de Dados:**
    * **Dinheiro:** Usei `NUMERIC(15,2)` ou `DECIMAL`. Jamais `FLOAT`, pois cálculos flutuantes geram erros de arredondamento em sistemas financeiros.
    * **Datas:** Usei inteiros para `Ano` e `Trimestre`, pois facilita a indexação e queries de agrupamento.

---

### 📍 4. API e Frontend (Item 4)

* **Framework (FastAPI):**
    * Escolhido pela performance superior ao Flask (Assíncrono) e pela geração automática de documentação (Swagger), agilizando o desenvolvimento.

* **Paginação e Busca:**
    * Implementei paginação no backend (`limit` e `page`).
    * **Justificativa:** Trafegar todos os dados para o frontend travaria o navegador do usuário. A busca também é feita no backend para aproveitar a performance do Pandas/Banco de Dados.

* **Frontend (Vue.js via CDN):**
    * **Decisão KISS:** Utilizei Vue via CDN em vez de configurar um ambiente complexo com Webpack/Vite.
    * **Justificativa:** Para este escopo, reduz drasticamente a complexidade de configuração ("setup fatigue"), permitindo focar na lógica de visualização e consumo da API.

---

**Autor:** Leonardo A. Roeda
