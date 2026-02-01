package org.example;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class TesteEstagio {

  // --- CONFIGURAÇÕES BÁSICAS ---
  // Pastas onde vamos fazer a bagunça organizada
  private static final String PASTA_TRABALHO = "./dados_teste";
  private static final String CSV_DETALHADO = "./Resultado_Final.csv";
  private static final String CSV_AGREGADO = "./despesas_agregadas.csv";
  private static final String ZIP_FINAL = "./ Teste_{Leonardo Roeda}.zip";

  public static void main(String[] args) {
    System.out.println(">>> Iniciando o Teste (Modo Estagiário: ON) <<<");

    try {
      // 1. Apaga tudo da vez passada pra não misturar dados
      limparPasta();

      // 2. Download: Baixa os arquivos fingindo ser o Google Chrome
      baixarTudo();

      // 3. Unzip: Tira os CSVs de dentro dos ZIPs da ANS
      descompactarArquivos();

      // 4. Memória: Carrega os nomes das empresas num HashMap (muito mais rápido que banco de dados pra isso)
      // Aqui tem o truque novo pra pegar a UF correta
      Map<String, String[]> memoriaOperadoras = lerCadastroOperadoras();

      // 5. O Trabalho Pesado: Lê os CSVs antigos (ISO), cruza com o cadastro (UTF-8) e salva pro Excel
      gerarRelatorioDetalhado(memoriaOperadoras);

      // 6. Matemática Chata: Calcula média e desvio padrão pro item 2.3
      gerarRelatorioAgregado();

      // 7. Entrega: Empacota tudo num ZIP só
      criarZipFinal();

      System.out.println("\n>>> TUDO PRONTO! PODE ABRIR O ZIP E SER FELIZ <<<");

    } catch (Exception e) {
      // mensagem de erro
      System.out.println("❌ Deu erro aqui, patrão: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private static void limparPasta() throws IOException {
    System.out.println("1. Limpando a mesa de trabalho...");
    File pasta = new File(PASTA_TRABALHO);
    if (pasta.exists()) {
      FileUtils.deleteDirectory(pasta); // Apaga a pasta e tudo dentro
    }
    pasta.mkdirs(); // Cria uma pasta nova
  }

  private static void baixarTudo() throws IOException {
    System.out.println("2. Baixando arquivos da ANS...");
    // Lista dos links que no meu caso nao deu erro
    baixarArquivo("https://dadosabertos.ans.gov.br/FTP/PDA/demonstracoes_contabeis/2025/3T2025.zip", "2025_3T.zip");
    baixarArquivo("https://dadosabertos.ans.gov.br/FTP/PDA/demonstracoes_contabeis/2025/2T2025.zip", "2025_2T.zip");
    baixarArquivo("https://dadosabertos.ans.gov.br/FTP/PDA/demonstracoes_contabeis/2024/4T2024.zip", "2024_4T.zip");
    // Esse aqui é o cadastro com os nomes das empresas
    baixarArquivo("https://dadosabertos.ans.gov.br/FTP/PDA/operadoras_de_plano_de_saude_ativas/Relatorio_cadop.csv", "cadastro.csv");
  }

  private static void baixarArquivo(String link, String nomeSalvar) throws IOException {
    System.out.print("   -> Tentando baixar " + nomeSalvar + "... ");
    URL url = new URL(link);
    HttpURLConnection conexao = (HttpURLConnection) url.openConnection();

    // TRUQUE: User-Agent para forcar ser um navegador especifico para nao dar erro
    conexao.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/121.0.0.0 Safari/537.36");

    try (InputStream entrada = conexao.getInputStream()) {
      Files.copy(entrada, new File(PASTA_TRABALHO, nomeSalvar).toPath(), StandardCopyOption.REPLACE_EXISTING);
      System.out.println("Sucesso!");
    } catch (Exception e) {
      System.out.println("Falhou (" + e.getMessage() + ")");
    }
  }

  private static void descompactarArquivos() throws IOException {
    System.out.println("3. Extraindo os ZIPs...");
    File[] arquivos = new File(PASTA_TRABALHO).listFiles();
    byte[] buffer = new byte[1024];

    if (arquivos != null) {
      for (File arquivo : arquivos) {
        if (arquivo.getName().endsWith(".zip")) {
          // Abre o ZIP
          try (ZipInputStream zip = new ZipInputStream(new FileInputStream(arquivo))) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
              // Só queremos CSV
              if (entrada.getName().toLowerCase().endsWith(".csv")) {
                // Cria um nome único  pra não sobrescrever
                File novo = new File(PASTA_TRABALHO, arquivo.getName().replace(".zip", "_dados.csv"));
                try (FileOutputStream fos = new FileOutputStream(novo)) {
                  int len;
                  while ((len = zip.read(buffer)) > 0) fos.write(buffer, 0, len);
                }
              }
            }
          }
        }
      }
    }
  }

  private static Map<String, String[]> lerCadastroOperadoras() throws IOException {
    System.out.println("4. Carregando cadastro na memória (UTF-8)...");
    Map<String, String[]> mapa = new HashMap<>();

    // Lista de UFs válidas, pq de outro jeito estava pegando numeros
    List<String> ufsValidas = Arrays.asList("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    // Importante: Leitura como UTF-8 para corrigir acentuacoes
    try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(PASTA_TRABALHO, "cadastro.csv")), StandardCharsets.UTF_8))) {
      String linha = br.readLine(); // Pula o cabeçalho
      while ((linha = br.readLine()) != null) {
        // Split -1 pra não ignorar colunas vazias no final
        String[] cols = linha.replace("\"", "").split(";", -1);

        if (cols.length > 2) {
          String reg = cols[0].trim();
          String cnpj = cols[1].trim();
          String razao = cols[2].trim();

          // --- O PULO DO GATO DA UF ---
          // Como a coluna da UF muda de lugar dependendo da linha,
          // a gente varre de trás pra frente até achar uma sigla válida (tipo SP, RJ).
          String uf = "ND";
          for (int i = cols.length - 1; i >= 0; i--) {
            String candidato = cols[i].trim().toUpperCase();
            if (candidato.length() == 2 && ufsValidas.contains(candidato)) {
              uf = candidato;
              break; // Achou? Para de procurar.
            }
          }

          mapa.put(reg, new String[]{cnpj, razao, uf});
        }
      }
    }
    return mapa;
  }

  private static void gerarRelatorioDetalhado(Map<String, String[]> mapaOperadoras) throws IOException {
    System.out.println("5. Gerando CSV Detalhado (Mixando Encodings)...");

    FileOutputStream fos = new FileOutputStream(CSV_DETALHADO);
    // TRUQUE DO EXCEL: Escreve BOM (Byte Order Mark) no começo do arquivo.
    // Sem isso, o Excel abre o arquivo UTF-8 achando que é ANSI e zoa todos os acentos.
    fos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

    try (Writer fw = new OutputStreamWriter(fos, StandardCharsets.UTF_8); CSVWriter writer = new CSVWriter(fw, ';', CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END)) {

      // Escreve cabeçalho
      writer.writeNext(new String[]{"RegistroANS", "CNPJ", "RazaoSocial", "Trimestre", "Ano", "UF", "ValorDespesas"}, false);

      File[] csvs = new File(PASTA_TRABALHO).listFiles((d, n) -> n.endsWith("dados.csv"));

      if (csvs != null) {
        // Leitor ISO-8859-1 (Porque os arquivos contábeis da ANS são VELHOS/LEGADO)
        Charset iso = Charset.forName("ISO-8859-1");
        var parser = new CSVParserBuilder().withSeparator(';').build();

        for (File csv : csvs) {
          String ano = csv.getName().substring(0, 4);
          String tri = csv.getName().substring(5, 6);

          try (CSVReader reader = new CSVReaderBuilder(new BufferedReader(new InputStreamReader(new FileInputStream(csv), iso))).withCSVParser(parser).build()) {
            String[] linha;
            reader.readNext(); // Pula cabeçalho original
            while ((linha = reader.readNext()) != null) {
              if (linha.length < 6) continue;

              // Filtro: Só queremos saber onde a empresa gastou dinheiro
              String desc = linha[3].toUpperCase();
              if (desc.contains("EVENTO") || desc.contains("SINISTRO") || desc.contains("DESPESA")) {
                String reg = linha[1];
                // Busca no nosso mapa (Join em memória)
                String[] dados = mapaOperadoras.getOrDefault(reg, new String[]{"N/D", "Operadora Desconhecida", "ND"});

                writer.writeNext(new String[]{reg, "'" + dados[0], // Aspas simples pro Excel não transformar CNPJ em notação científica (1.2E+14)
                  dados[1],       // Razão social (Já limpa em UTF-8)
                  tri, ano, dados[2],       // UF (Agora correta!)
                  linha[5].replace(".", "").replace(",", ".") // Valor padrão americano (1500.00)
                }, false);
              }
            }
          } catch (CsvValidationException e) {
            throw new RuntimeException(e);
          }
        }
      }
    }
  }

  private static void gerarRelatorioAgregado() throws IOException {
    System.out.println("6. Calculando estatísticas (Média e Desvio Padrão)...");

    Map<String, List<Double>> agrupador = new HashMap<>();

    // Reutiliza o CSV detalhado que acabamos de criar (pra garantir consistência)
    var parser = new CSVParserBuilder().withSeparator(';').build();
    try (CSVReader reader = new CSVReaderBuilder(new BufferedReader(new InputStreamReader(new FileInputStream(CSV_DETALHADO), StandardCharsets.UTF_8))).withCSVParser(parser).build()) {

      reader.readNext(); // Pula cabeçalho
      String[] cols;

      while ((cols = reader.readNext()) != null) {
        if (cols.length >= 7) {
          String razao = cols[2];
          String uf = cols[5];
          if (uf == null || uf.trim().isEmpty()) uf = "ND"; // Proteção contra null

          try {
            double valor = Double.parseDouble(cols[6]);
            String chave = razao + ";" + uf; // Chave composta pra agrupar

            agrupador.putIfAbsent(chave, new ArrayList<>());
            agrupador.get(chave).add(valor);
          } catch (NumberFormatException e) {
            // Se não for número, ignora
          }
        }
      }
    } catch (CsvValidationException e) {
      throw new RuntimeException(e);
    }

    // Prepara pra salvar o relatório
    FileOutputStream fos = new FileOutputStream(CSV_AGREGADO);
    fos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // BOM de novo pro Excel

    try (Writer fw = new OutputStreamWriter(fos, StandardCharsets.UTF_8); CSVWriter writer = new CSVWriter(fw, ';', CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END)) {

      writer.writeNext(new String[]{"RazaoSocial", "UF", "TotalDespesas", "MediaTrimestral", "DesvioPadrao"}, false);

      List<String[]> linhasFinais = new ArrayList<>();

      for (Map.Entry<String, List<Double>> entry : agrupador.entrySet()) {
        String[] chaves = entry.getKey().split(";", -1);
        String nome = chaves.length > 0 ? chaves[0] : "Desconhecido";
        String estado = chaves.length > 1 ? chaves[1] : "ND";

        List<Double> valores = entry.getValue();

        // Matemática básica
        double soma = 0;
        for (double v : valores) soma += v;
        double media = soma / valores.size();

        // Matemática chata (Desvio Padrão)
        double somaQuadrados = 0;
        for (double v : valores) somaQuadrados += Math.pow(v - media, 2);
        double desvio = Math.sqrt(somaQuadrados / valores.size());

        linhasFinais.add(new String[]{nome, estado, String.format(Locale.US, "%.2f", soma), String.format(Locale.US, "%.2f", media), String.format(Locale.US, "%.2f", desvio)});
      }

      // Ordena do maior gasto pro menor
      linhasFinais.sort((a, b) -> Double.compare(Double.parseDouble(b[2]), Double.parseDouble(a[2])));

      writer.writeAll(linhasFinais, false);
    }
  }

  private static void criarZipFinal() throws IOException {
    System.out.println("7. Compactando tudo para entrega...");
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(ZIP_FINAL))) {
      addToZip(CSV_DETALHADO, zos);
      addToZip(CSV_AGREGADO, zos);
    }
  }

  private static void addToZip(String path, ZipOutputStream zos) throws IOException {
    File file = new File(path);
    if (file.exists()) {
      zos.putNextEntry(new ZipEntry(file.getName()));
      try (FileInputStream fis = new FileInputStream(file)) {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = fis.read(buffer)) > 0) zos.write(buffer, 0, len);
      }
      zos.closeEntry();
    }
  }
}