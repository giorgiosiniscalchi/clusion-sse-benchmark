# Documentazione Tecnica del Progetto: Clusion SSE Benchmark

Questa documentazione descrive l'architettura, le componenti principali e il flusso di esecuzione del software sviluppato per il benchmarking di schemi di Searchable Symmetric Encryption (SSE).

## Obiettivo del Progetto
Il software ha l'obiettivo di valutare e confrontare le prestazioni di diversi schemi SSE implementati dalla libreria **Clusion**. Le metriche analizzate includono tempo di costruzione dell'indice, dimensione dell'indice, latenza delle query, utilizzo della memoria e throughput. Inoltre, viene fornita un'analisi automatica della sicurezza (Leakage Analysis).

## Architettura del Software

Il progetto è strutturato in package Java che separano logicamente le responsabilità:

### 1. Main Entry Point (`it.thesis.sse`)
*   **`ClusionBenchmark`**: È la classe principale che avvia il programma. Gestisce il parsing degli argomenti da riga di comando (via Apache Commons CLI), configura l'ambiente di esecuzione e orchestra l'intero processo di benchmark richiamando `BenchmarkRunner`.

### 2. Gestione Dataset (`it.thesis.sse.dataset`)
Questo modulo si occupa di caricare e strutturare i dati su cui verranno eseguiti i test.
*   **`DatasetLoader`**: Legge i file generati dallo script Python. Supporta il caricamento di:
    *   *Index Inverso* (`keyword_index.json`): Mappa parola chiave -> lista di ID documenti.
    *   *Documenti* (`dataset.json`): Contenuto testuale dei documenti.
    *   *Query di Test* (`test_queries.json`): Set predefinito di query per garantire riproducibilità.
*   **`Document`**: Rappresentazione a oggetti di un singolo documento, contenente ID e contenuto.
*   **`KeywordExtractor`**: Utilità per estrarre parole chiave dai testi se necessario (tokenizzazione).

### 3. Schemi SSE (`it.thesis.sse.schemes`)
Questo package fornisce un'astrazione uniforme sopra la libreria Clusion, permettendo al benchmark di trattare tutti gli schemi allo stesso modo.
*   **`SSEScheme` (Interfaccia)**: Definisce i metodi che ogni wrapper deve implementare: `setup()`, `buildIndex()`, `search()`.
*   **`SchemeFactory`**: Pattern Factory per istanziare dinamicamente gli schemi in base al nome (es. "ZMF", "2Lev-RR").
*   **Implementazioni Specifiche**:
    *   `ZMFScheme`: Wrapper per lo schema Zipfian Multi-Keyword (basilare, risposta lineare).
    *   `TwoLevScheme`: Wrapper per lo schema a Due Livelli (risposta sub-lineare).
    *   `IEXTwoLevScheme`: Supporto per query booleane (disgiuntive/congiuntive) su struttura a due livelli.
    *   `IEXZMFScheme`: Supporto per query booleane su struttura ZMF.

### 4. Motore di Benchmark (`it.thesis.sse.benchmark`)
Il cuore pulsante dell'applicazione che esegue le misurazioni.
*   **`BenchmarkRunner`**: Cicla attraverso gli schemi selezionati. Per ognuno:
    1.  Esegue una fase di *Warmup* (per riscaldare la JVM JIT).
    2.  Esegue le *Iterazioni di Misurazione* reali.
    3.  Colleziona i risultati.
*   **`BenchmarkMetrics`**: Classe contenitore per statistiche aggregate (tempo medio, p95, throughput, memoria di picco).
*   **`QueryGenerator`**: Genera query sintetiche o seleziona query dal set di test in base alla distribuzione definita.
*   **`QueryMetric`**: Registra i dati di una singola esecuzione di query.

### 5. Sicurezza (`it.thesis.sse.security`)
*   **`LeakageAnalyzer`**: Analizza il profilo di leakage teorico di ogni schema. Contiene un enum `LeakageType` (Pattern di ricerca, Pattern di accesso, ecc.) e valuta se uno schema rivela tali informazioni.
*   **`SecurityReport`**: Genera un report leggibile con punteggio di sicurezza e vulnerabilità potenziali.

### 6. Utility e Output (`it.thesis.sse.util`)
*   **`JsonExporter`**: Serializza i risultati del benchmark in formato JSON per analisi successive.
*   **`ConsoleFormatter`**: Gestisce l'output formattato a terminale (tabelle ASCII, barre di progresso).
*   **`CryptoUtils`**: Funzioni crittografiche di supporto (es. generazione chiavi AES/PRNG).

## Flusso di Dati e Test

1.  **Generazione Dati (Pre-esecuzione)**:
    Uno script Python (`dataset/generate_dataset.py`) crea un corpus sintetico di cartelle cliniche elettroniche (E-Health), generando un *indice inverso* e un set di *query di test*.

2.  **Caricamento**:
    All'avvio, `DatasetLoader` deserializza il JSON in memoria.

3.  **Ciclo di Test (per ogni schema)**:
    *   **Setup**: Viene istanziato lo schema e generate le chiavi crittografiche.
    *   **Indicizzazione**: Viene misurato il tempo per costruire l'indice cifrato (EDB) a partire dall'indice inverso in chiaro.
    *   **Query**:
        *   Vengono eseguite query singole o booleane.
        *   Il sistema misura la latenza (in microsecondi/millisecondi) per ogni ricerca.
        *   Viene verificata la correttezza confrontando i risultati cifrati con la *Ground Truth* (verità di base) calcolata in chiaro.

4.  **Reporting**:
    I risultati grezzi vengono aggregati e salvati in `results/` come JSON, CSV e un riepilogo Markdown.

## Guida all'Esecuzione

Per avviare l'intera suite di test, utilizzare il comando Maven che compila il progetto ed esegue il JAR:

```bash
mvn package -DskipTests; java -jar target/clusion-sse-benchmark-1.0-SNAPSHOT-jar-with-dependencies.jar
```
