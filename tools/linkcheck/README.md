# linkcheck

Checks that every class, method and field below a package prefix (default `xaero/pac/`) that one or more
consumer jars reference exists in a provider jar in a way the JVM can link. It catches the
`NoSuchMethodError` / `NoSuchFieldError` / `NoClassDefFoundError` class of crashes that other mods
(Xaero's World Map, Minimap) cause when they use API our jar does not have.

One Java 21 source file, no dependencies, no build tool.

## Usage

```
java tools/linkcheck/LinkCheck.java --provider <jar> --consumer <jar> [--consumer <jar> ...]
                                    [--prefix xaero/pac/] [--report <file>] [--verbose] [--include-self]
```

| Option | Meaning |
| --- | --- |
| `--provider` | Jar that must provide the API (our built mod jar). Nested jars are read too. |
| `--consumer` | Jar whose references are checked. Repeatable. Every `.class` in it and in jars nested at any depth (`META-INF/jars`, `META-INF/jarjar`, any `*.jar` entry) is scanned. |
| `--prefix` | Only references whose owner class starts with this prefix. Dots are accepted (`xaero.pac.`). |
| `--report` | Write the same text to a file (UTF-8). |
| `--verbose` | Also list every checked reference with its status. |
| `--include-self` | Also require classes the consumer defines itself (normally skipped). Use with the same jar as provider and consumer to smoke-test the tool. |

Example:

```
java tools/linkcheck/LinkCheck.java --provider build/libs/opac-team-claims-neoforge.jar ^
  --consumer WorldMap.jar --consumer Minimap.jar --report linkcheck.txt
```

Self-test (builds synthetic jars in the temp directory, runs the real command line, exit 0 = green):

```
java tools/linkcheck/LinkCheckSelfTest.java
```

## What is checked

Per consumer, from the constant pool of every class: `CONSTANT_Class` (array types use the element class),
`Fieldref`, `Methodref`, `InterfaceMethodref` with an owner under the prefix. Resolution follows JVMS 5.4.3.2 to
5.4.3.4 (simplified), name and descriptor must match exactly:

- Field: class, then super-interfaces recursively, then the superclass chain.
- Method: class and superclass chain, then all super-interfaces of those classes (static and private interface methods are not inherited). For an interface owner: the interface, `java.lang.Object` public instance methods, then super-interfaces. `<init>` is not inherited.
- Hierarchy parts under `java/` and `javax/` that are not in the provider are resolved through the running JDK.

Reported as **MISSING** (exit 1): class not found, member not found (with a hint when the name exists with another
descriptor).

Reported as **ERRORS** (exit 1):

- `Methodref` on an interface or `InterfaceMethodref` on a class (`IncompatibleClassChangeError`).
- Static mismatch: the consumer's bytecode uses a member as static (`invokestatic`, `getstatic`, `putstatic`, static method handle) but it is not, or the other way round (`invokevirtual`, `invokespecial`, `invokeinterface`, `getfield`, `putfield`). The `Code` attributes are walked instruction by instruction (`tableswitch`, `lookupswitch`, `wide` included).
- Member is `private` in the provider and used from outside its nest (`IllegalAccessError`).

Reported as **UNVERIFIABLE** (does not fail the run): the member was not found and the search had to continue into a
class that is neither in the provider nor under `java/`, `javax/` (Minecraft, other mods), reported with
`hierarchy leaves the provider at <class>`.

Reported as **WARNINGS** (do not fail the run): package-private or protected members and package-private classes
used from another package, and classes whose bytecode walk failed (their static/non-static usage is then unknown).

## What is not checked

- Types that only occur inside descriptors (not as a Class constant, owner or in a reference).
- Static/non-static usage for references that appear in no instruction and no method handle.
- Access rules beyond the above (protected access from subclasses is only a warning, `final` overrides, abstract
  or sealed rules, module access).
- Which member the JVM would pick when the same member exists in several places of the hierarchy: the first one found in
  JVMS order within the known part of the hierarchy wins, even if an unknown class earlier in the hierarchy might declare it.
- `META-INF/versions/**` classes in the provider (multi-release variants) are ignored; consumer classes there are scanned.
- Reflection, `ServiceLoader`, Mixin targets and string-based lookups.

## Exit codes

| Code | Meaning |
| --- | --- |
| 0 | No MISSING and no ERRORS. UNVERIFIABLE entries and warnings are printed but allowed. A consumer with no reference under the prefix prints `0 references with prefix` and is not an error. |
| 1 | At least one MISSING or ERROR reference. |
| 2 | Usage problem or I/O problem (missing or corrupt jar, unknown option, provider without classes). |

## Report format

Per consumer: classes scanned, distinct references (classes / methods / fields), counts of OK, MISSING, ERROR,
UNVERIFIABLE and warnings, then the sorted lists `MISSING`, `ERRORS`, `UNVERIFIABLE`, `WARNINGS`
(and `ALL CHECKED REFERENCES` with `--verbose`). Entries look like

```
xaero.pac.client.claims.api.IClientClaimsManagerAPI.usingAnchorBasedClaiming()Z  (used by xaero.map.mods.pac.SupportOpenPartiesAndClaims)  -- method not found
```

Fields are written `owner.name:descriptor`, classes as the plain class name.
