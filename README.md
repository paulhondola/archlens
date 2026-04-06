# Java Decompiler

A command-line tool that uses Java reflection to introspect compiled `.jar` files and generate UML class diagrams. Supports **yUML** and **PlantUML** output formats.

---

## How It Works

The tool runs a strict, layered pipeline:

```
JAR file
   │
   ▼
JarLoader          load Class<?> objects via URLClassLoader
   │
   ▼
ClassFilter        apply --ignore patterns
   │
   ▼
ClassInspector     extract fields, methods, relationships via reflection
   │
   ▼
UmlFormatter       render to yUML or PlantUML string
   │
   ▼
stdout / file
```

Relationships are discovered entirely through reflection:

| Relationship  | Source                                          |
|---------------|-------------------------------------------------|
| `extends`     | `clazz.getSuperclass()`                         |
| `implements`  | `clazz.getInterfaces()`                         |
| `association` | Field types (including generic type parameters) |
| `dependency`  | Method parameter and return types               |

> Aggregation and composition cannot be distinguished via reflection — both are reported as `association`. Cardinality is not included.

---

## Package Structure

```
org.paul/
├── Main.java                  # CLI entry point — orchestrates the pipeline
├── config/
│   └── DecompileConfig.java   # Immutable config record
├── loader/
│   └── JarLoader.java         # Opens JAR, loads classes via URLClassLoader
├── model/
│   ├── ClassInfo.java         # Immutable snapshot of a single class
│   ├── Relationship.java      # Sealed type: Extends | Implements | Association | Dependency
│   └── FieldInfo.java         # Field name, type string, access modifier char
├── introspection/
│   └── ClassInspector.java    # Extracts ClassInfo from a Class<?> using reflect
├── filter/
│   └── ClassFilter.java       # Applies ignore patterns before introspection
└── formatter/
    ├── UmlFormatter.java      # Interface: format(List<ClassInfo>, config) → String
    ├── YumlFormatter.java     # yUML output (SIMPLE / CLASSES modes)
    └── PlantUmlFormatter.java # PlantUML output
```

---

## Build

Requires Java 25+ and Maven.

```bash
mvn package
```

Produces a self-contained fat JAR at `target/Java-Decompiler-1.0-SNAPSHOT.jar` with all dependencies bundled (including picocli).

To build and skip tests:

```bash
mvn package -DskipTests
```

---

## Usage

```
Usage: java-decompiler [-hV] --format=<format> [--ignore=PATTERN[,PATTERN...]]
                       [--output=FILE] [--yuml-mode=MODE] JAR

      JAR                 Path to the JAR file to analyse.
      --format=<format>   Output format: yuml, plantuml.
      --ignore=PATTERN    Class name patterns to exclude, comma-separated or
                            repeated (e.g. 'java.lang.*').
      --output=FILE       Write output to FILE instead of stdout.
      --yuml-mode=MODE    yUML rendering mode (only with --format yuml):
                            SIMPLE, CLASSES. Default: SIMPLE.
  -h, --help              Show this help message and exit.
  -V, --version           Print version information and exit.
```

### Examples

```bash
# PlantUML diagram to stdout
java -jar target/Java-Decompiler-1.0-SNAPSHOT.jar MyLib.jar --format plantuml

# yUML with full member details, written to a file
java -jar target/Java-Decompiler-1.0-SNAPSHOT.jar MyLib.jar --format yuml --yuml-mode CLASSES --output diagram.yuml

# Ignore multiple packages
java -jar target/Java-Decompiler-1.0-SNAPSHOT.jar MyLib.jar --format plantuml \
  --ignore "java.lang.*" --ignore "java.util.*"

# Same using comma-separated form
java -jar target/Java-Decompiler-1.0-SNAPSHOT.jar MyLib.jar --format plantuml \
  --ignore "java.lang.*,java.util.*"
```

---

## Output Formats

### yUML — SIMPLE mode

One line per class, one line per relationship. No member details.

```
[ClassName]
[Interface]^-.-[Implementor]
[Parent]^-[Child]
[Owner]->[Target]
```

### yUML — CLASSES mode

Same relationships, with fields and methods embedded in the class node:

```
[ClassName|- field:Type|+ method()]
```

Access modifier symbols:

| Java modifier  | Symbol |
|----------------|--------|
| `private`      | `-`    |
| `protected`    | `#`    |
| `public`       | `+`    |
| package-private| `~`    |

Parameterized types render as `ArrayList of Observer`.

### PlantUML

```
@startuml

interface InterfaceName{
  method()
}

class ClassName{
  -fieldName:FieldType
  methodName()
}

Parent <|--- Child
Owner ---> Target

@enduml
```

---

## Running Tests

```bash
mvn test
```

Tests cover each layer independently (unit) and validate end-to-end output against fixture files in `src/test/java/`:

| Fixture directory    | Contents                              |
|----------------------|---------------------------------------|
| `tempsensor/`        | TempSensor.jar — yUML and PlantUML    |
| `eventnotifier/`     | EventNotifier.jar — yUML simple/classes |
| `selftest/`          | Tool analysing itself (see below)     |

---

## Self-Test

These three commands run the decompiler against its own fat JAR and write the results into the `selftest/` fixture directory. Picocli internals are excluded via `--ignore`.

```bash
java -jar target/Java-Decompiler-1.0-SNAPSHOT.jar target/Java-Decompiler-1.0-SNAPSHOT.jar \
  --format plantuml \
  --output "src/test/java/selftest/selftest.puml" \
  --ignore "picocli.*"

java -jar target/Java-Decompiler-1.0-SNAPSHOT.jar target/Java-Decompiler-1.0-SNAPSHOT.jar \
  --format yuml --yuml-mode SIMPLE \
  --output "src/test/java/selftest/selftest-simple.yuml" \
  --ignore "picocli.*"

java -jar target/Java-Decompiler-1.0-SNAPSHOT.jar target/Java-Decompiler-1.0-SNAPSHOT.jar \
  --format yuml --yuml-mode CLASSES \
  --output "src/test/java/selftest/selftest-classes.yuml" \
  --ignore "picocli.*"
```
