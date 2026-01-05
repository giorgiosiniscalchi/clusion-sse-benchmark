#!/usr/bin/env python3
"""
E-Health Dataset Generator for SSE Benchmark
Generates synthetic medical records for testing Searchable Symmetric Encryption schemes.
"""

import argparse
import json
import os
import random
import string
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List, Set

# Try to import Faker for realistic names, fall back to simple names if not available
try:
    from faker import Faker
    fake = Faker('it_IT')
    USE_FAKER = True
except ImportError:
    USE_FAKER = False
    print("Warning: Faker not installed. Using simple name generation.")

# Medical data constants
DEPARTMENTS = [
    "Cardiologia", "Neurologia", "Oncologia", "Ortopedia", "Pediatria",
    "Ginecologia", "Urologia", "Dermatologia", "Pneumologia", "Gastroenterologia",
    "Nefrologia", "Endocrinologia", "Reumatologia", "Ematologia", "Geriatria",
    "Psichiatria", "Chirurgia Generale", "Medicina Interna", "Pronto Soccorso", "Terapia Intensiva"
]

DIAGNOSES = [
    # Cardiologia
    "ipertensione arteriosa", "insufficienza cardiaca", "aritmia", "infarto miocardico",
    "angina pectoris", "fibrillazione atriale", "tachicardia", "bradicardia",
    # Neurologia
    "emicrania", "epilessia", "ictus", "sclerosi multipla", "parkinson",
    "alzheimer", "neuropatia periferica", "cefalea tensiva",
    # Oncologia
    "carcinoma polmonare", "carcinoma mammario", "carcinoma colon-retto",
    "leucemia", "linfoma", "melanoma", "carcinoma prostatico",
    # Ortopedia
    "frattura", "artrosi", "artrite reumatoide", "osteoporosi", "ernia discale",
    "tendinite", "borsite", "scoliosi",
    # Generale
    "diabete mellito tipo 2", "diabete mellito tipo 1", "ipotiroidismo", "ipertiroidismo",
    "anemia", "insufficienza renale", "cirrosi epatica", "gastrite",
    "ulcera peptica", "colite", "bronchite cronica", "asma bronchiale",
    "polmonite", "influenza", "covid-19", "allergia alimentare"
]

TREATMENTS = [
    # Farmaci
    "ramipril 5mg", "metformina 1000mg", "atorvastatina 20mg", "omeprazolo 20mg",
    "aspirina 100mg", "warfarin 5mg", "insulina glargine", "levotiroxina 50mcg",
    "amlodipina 5mg", "bisoprololo 2.5mg", "furosemide 25mg", "prednisone 25mg",
    # Procedure
    "intervento chirurgico", "radioterapia", "chemioterapia", "fisioterapia",
    "riabilitazione cardiologica", "dialisi", "trasfusione", "biopsia",
    "endoscopia", "colonscopia", "TAC", "risonanza magnetica", "ecografia",
    "elettrocardiogramma", "holter cardiaco", "spirometria"
]

CLINICAL_NOTES_TEMPLATES = [
    "Paziente presenta {symptoms}. Si consiglia {recommendation}.",
    "Esame obiettivo: {findings}. Diagnosi: {diagnosis}. Terapia: {therapy}.",
    "Controllo di follow-up. Paziente in condizioni {condition}. Proseguire terapia in corso.",
    "Ricovero per {reason}. Durata prevista: {duration} giorni.",
    "Dimissione con {prescription}. Prossimo controllo tra {followup} settimane.",
    "Esami di laboratorio: {lab_results}. Valori nella norma eccetto {exceptions}.",
    "Visita specialistica: {specialist_notes}. Indicata {procedure}."
]

SYMPTOMS = [
    "dolore toracico", "dispnea", "astenia", "cefalea", "vertigini",
    "nausea", "vomito", "febbre", "tosse", "dolore addominale",
    "edema", "palpitazioni", "sincope", "parestesie", "artralgia"
]

SIMPLE_FIRST_NAMES = [
    "Mario", "Giuseppe", "Giovanni", "Francesco", "Antonio", "Luigi", "Marco",
    "Paolo", "Andrea", "Roberto", "Maria", "Rosa", "Anna", "Francesca", "Giulia",
    "Laura", "Sara", "Elena", "Alessia", "Silvia"
]

SIMPLE_LAST_NAMES = [
    "Rossi", "Russo", "Ferrari", "Esposito", "Bianchi", "Romano", "Colombo",
    "Ricci", "Marino", "Greco", "Bruno", "Gallo", "Conti", "Costa", "Mancini"
]


def generate_patient_id() -> str:
    """Generate a unique patient ID."""
    return f"PAT{random.randint(100000, 999999)}"


def generate_document_id() -> str:
    """Generate a unique document ID."""
    return f"DOC{random.randint(1000000, 9999999)}"


def get_random_name() -> tuple:
    """Generate a random first and last name."""
    if USE_FAKER:
        return fake.first_name(), fake.last_name()
    return random.choice(SIMPLE_FIRST_NAMES), random.choice(SIMPLE_LAST_NAMES)


def generate_clinical_notes() -> str:
    """Generate random clinical notes."""
    template = random.choice(CLINICAL_NOTES_TEMPLATES)
    
    replacements = {
        "{symptoms}": ", ".join(random.sample(SYMPTOMS, random.randint(1, 3))),
        "{recommendation}": random.choice(TREATMENTS),
        "{findings}": random.choice(["obiettività cardiaca nei limiti", "addome trattabile", "torace eupnoico"]),
        "{diagnosis}": random.choice(DIAGNOSES),
        "{therapy}": random.choice(TREATMENTS),
        "{condition}": random.choice(["stabili", "migliorate", "stazionarie"]),
        "{reason}": random.choice(DIAGNOSES),
        "{duration}": str(random.randint(2, 14)),
        "{prescription}": random.choice(TREATMENTS),
        "{followup}": str(random.randint(1, 8)),
        "{lab_results}": "emocromo, glicemia, creatinina, transaminasi",
        "{exceptions}": random.choice(["glicemia lievemente elevata", "lieve anemia", "creatinina ai limiti"]),
        "{specialist_notes}": random.choice(["quadro compatibile con", "si conferma diagnosi di", "da escludere"]) + " " + random.choice(DIAGNOSES),
        "{procedure}": random.choice(TREATMENTS)
    }
    
    for key, value in replacements.items():
        template = template.replace(key, value)
    
    return template


def generate_document(doc_index: int) -> Dict:
    """Generate a single e-health document."""
    first_name, last_name = get_random_name()
    age = random.randint(18, 95)
    department = random.choice(DEPARTMENTS)
    
    # Number of diagnoses and treatments follow a power law distribution
    num_diagnoses = min(random.randint(1, 5), len(DIAGNOSES))
    num_treatments = min(random.randint(1, 6), len(TREATMENTS))
    
    diagnoses = random.sample(DIAGNOSES, num_diagnoses)
    treatments = random.sample(TREATMENTS, num_treatments)
    
    # Generate admission date within last 5 years
    days_ago = random.randint(0, 1825)
    admission_date = (datetime.now() - timedelta(days=days_ago)).strftime("%Y-%m-%d")
    
    clinical_notes = generate_clinical_notes()
    
    # Build keywords list (all searchable terms)
    keywords = set()
    keywords.add(first_name.lower())
    keywords.add(last_name.lower())
    keywords.add(department.lower())
    for d in diagnoses:
        keywords.update(d.lower().split())
    for t in treatments:
        keywords.update(t.lower().split())
    # Add age ranges as keywords
    if age < 30:
        keywords.add("giovane")
    elif age < 50:
        keywords.add("adulto")
    elif age < 70:
        keywords.add("anziano")
    else:
        keywords.add("molto_anziano")
    
    return {
        "docId": generate_document_id(),
        "patientId": generate_patient_id(),
        "firstName": first_name,
        "lastName": last_name,
        "age": age,
        "department": department,
        "diagnoses": diagnoses,
        "treatments": treatments,
        "admissionDate": admission_date,
        "clinicalNotes": clinical_notes,
        "keywords": sorted(list(keywords))
    }


def generate_text_content(doc: Dict) -> str:
    """Generate text file content for a document (for Clusion file-based indexing)."""
    lines = [
        f"Patient ID: {doc['patientId']}",
        f"Document ID: {doc['docId']}",
        f"Name: {doc['firstName']} {doc['lastName']}",
        f"Age: {doc['age']}",
        f"Department: {doc['department']}",
        f"Admission Date: {doc['admissionDate']}",
        "",
        "Diagnoses:",
        *[f"  - {d}" for d in doc['diagnoses']],
        "",
        "Treatments:",
        *[f"  - {t}" for t in doc['treatments']],
        "",
        "Clinical Notes:",
        doc['clinicalNotes'],
        "",
        "Keywords:",
        ", ".join(doc['keywords'])
    ]
    return "\n".join(lines)


def build_keyword_index(documents: List[Dict]) -> Dict[str, List[str]]:
    """Build an inverted index from keywords to document IDs."""
    index = {}
    for doc in documents:
        doc_id = doc['docId']
        for keyword in doc['keywords']:
            if keyword not in index:
                index[keyword] = []
            index[keyword].append(doc_id)
    return index


def compute_statistics(documents: List[Dict], keyword_index: Dict[str, List[str]]) -> Dict:
    """Compute dataset statistics."""
    total_keywords = sum(len(doc['keywords']) for doc in documents)
    keyword_frequencies = {k: len(v) for k, v in keyword_index.items()}
    
    return {
        "numDocuments": len(documents),
        "numUniqueKeywords": len(keyword_index),
        "avgKeywordsPerDocument": round(total_keywords / len(documents), 2),
        "minKeywordsPerDocument": min(len(doc['keywords']) for doc in documents),
        "maxKeywordsPerDocument": max(len(doc['keywords']) for doc in documents),
        "avgDocumentsPerKeyword": round(sum(keyword_frequencies.values()) / len(keyword_frequencies), 2),
        "minDocumentsPerKeyword": min(keyword_frequencies.values()),
        "maxDocumentsPerKeyword": max(keyword_frequencies.values()),
        "topKeywords": sorted(keyword_frequencies.items(), key=lambda x: -x[1])[:20],
        "departments": list(set(doc['department'] for doc in documents)),
        "ageDistribution": {
            "under30": sum(1 for doc in documents if doc['age'] < 30),
            "30to49": sum(1 for doc in documents if 30 <= doc['age'] < 50),
            "50to69": sum(1 for doc in documents if 50 <= doc['age'] < 70),
            "70plus": sum(1 for doc in documents if doc['age'] >= 70)
        }
    }


def generate_test_queries(keyword_index: Dict[str, List[str]], num_queries: int = 50) -> List[Dict]:
    """Generate test queries for benchmarking."""
    queries = []
    keywords = list(keyword_index.keys())
    
    # Sort keywords by frequency
    by_frequency = sorted(keywords, key=lambda k: len(keyword_index[k]))
    rare_keywords = by_frequency[:len(by_frequency)//3]
    common_keywords = by_frequency[-len(by_frequency)//3:]
    medium_keywords = by_frequency[len(by_frequency)//3:-len(by_frequency)//3]
    
    # Single keyword queries - mix of frequencies
    for category, kw_list in [("rare", rare_keywords), ("medium", medium_keywords), ("common", common_keywords)]:
        sample_size = min(5, len(kw_list))
        for kw in random.sample(kw_list, sample_size):
            queries.append({
                "type": "single",
                "category": category,
                "keywords": [kw],
                "expectedResults": len(keyword_index[kw])
            })
    
    # AND queries (conjunction)
    for _ in range(10):
        kw1, kw2 = random.sample(keywords, 2)
        results1 = set(keyword_index[kw1])
        results2 = set(keyword_index[kw2])
        intersection = results1 & results2
        queries.append({
            "type": "AND",
            "category": "conjunction",
            "keywords": [kw1, kw2],
            "expectedResults": len(intersection)
        })
    
    # OR queries (disjunction)
    for _ in range(10):
        kw1, kw2 = random.sample(keywords, 2)
        results1 = set(keyword_index[kw1])
        results2 = set(keyword_index[kw2])
        union = results1 | results2
        queries.append({
            "type": "OR",
            "category": "disjunction",
            "keywords": [kw1, kw2],
            "expectedResults": len(union)
        })
    
    return queries


def main():
    parser = argparse.ArgumentParser(description='Generate synthetic e-health dataset for SSE benchmarking')
    parser.add_argument('--num-docs', type=int, default=1000, help='Number of documents to generate')
    parser.add_argument('--output-dir', type=str, default='./data', help='Output directory')
    parser.add_argument('--seed', type=int, default=None, help='Random seed for reproducibility')
    args = parser.parse_args()
    
    if args.seed is not None:
        random.seed(args.seed)
        if USE_FAKER:
            Faker.seed(args.seed)
    
    output_dir = Path(args.output_dir)
    docs_dir = output_dir / "documents"
    
    # Create directories
    output_dir.mkdir(parents=True, exist_ok=True)
    docs_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"Generating {args.num_docs} documents...")
    
    # Generate documents
    documents = []
    for i in range(args.num_docs):
        doc = generate_document(i)
        documents.append(doc)
        
        # Write individual text file
        text_content = generate_text_content(doc)
        with open(docs_dir / f"{doc['docId']}.txt", 'w', encoding='utf-8') as f:
            f.write(text_content)
        
        if (i + 1) % 100 == 0:
            print(f"  Generated {i + 1}/{args.num_docs} documents")
    
    # Build keyword index
    print("Building keyword index...")
    keyword_index = build_keyword_index(documents)
    
    # Compute statistics
    print("Computing statistics...")
    stats = compute_statistics(documents, keyword_index)
    
    # Generate test queries
    print("Generating test queries...")
    test_queries = generate_test_queries(keyword_index)
    
    # Save files
    print("Saving files...")
    
    with open(output_dir / "dataset.json", 'w', encoding='utf-8') as f:
        json.dump(documents, f, indent=2, ensure_ascii=False)
    
    with open(output_dir / "keyword_index.json", 'w', encoding='utf-8') as f:
        json.dump(keyword_index, f, indent=2, ensure_ascii=False)
    
    with open(output_dir / "statistics.json", 'w', encoding='utf-8') as f:
        json.dump(stats, f, indent=2, ensure_ascii=False)
    
    with open(output_dir / "test_queries.json", 'w', encoding='utf-8') as f:
        json.dump(test_queries, f, indent=2, ensure_ascii=False)
    
    print(f"\n=== Generation Complete ===")
    print(f"Documents generated: {len(documents)}")
    print(f"Unique keywords: {len(keyword_index)}")
    print(f"Average keywords per document: {stats['avgKeywordsPerDocument']}")
    print(f"Test queries generated: {len(test_queries)}")
    print(f"\nOutput directory: {output_dir.absolute()}")
    print(f"  - dataset.json: Complete dataset")
    print(f"  - documents/: Individual text files ({len(documents)} files)")
    print(f"  - keyword_index.json: Inverted index")
    print(f"  - statistics.json: Dataset statistics")
    print(f"  - test_queries.json: Benchmark queries")


if __name__ == "__main__":
    main()
