import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Self-test for LinkCheck.java. Builds a synthetic provider and consumer jars in a temp directory and runs the
 * real LinkCheck command line against them.
 *
 * Run from the repository root:  java tools/linkcheck/LinkCheckSelfTest.java
 * (optional first argument: path of LinkCheck.java). Exit code 0 = all expectations met, 1 = at least one failed,
 * 2 = the test harness itself failed.
 */
public final class LinkCheckSelfTest {

	private static int passed;
	private static final List<String> failures = new ArrayList<>();
	private static Path linkCheck;

	public static void main(String[] args) {
		Path temp = null;
		try {
			linkCheck = locateLinkCheck(args);
			temp = Files.createTempDirectory("linkcheck-selftest");
			runAll(temp);
		} catch (Exception e) {
			System.err.println("self-test harness failure: " + e);
			e.printStackTrace();
			System.exit(2);
		} finally {
			if (temp != null) {
				deleteRecursively(temp);
			}
		}
		System.out.println();
		System.out.println("passed expectations: " + passed);
		if (failures.isEmpty()) {
			System.out.println("SELFTEST OK");
			return;
		}
		System.out.println("failed expectations: " + failures.size());
		for (String f : failures) {
			System.out.println("  FAIL: " + f);
		}
		System.out.println("SELFTEST FAILED");
		System.exit(1);
	}

	private static Path locateLinkCheck(String[] args) throws IOException {
		List<Path> candidates = new ArrayList<>();
		if (args.length > 0) {
			candidates.add(Paths.get(args[0]));
		}
		candidates.add(Paths.get("tools", "linkcheck", "LinkCheck.java"));
		candidates.add(Paths.get("LinkCheck.java"));
		for (Path c : candidates) {
			if (Files.isRegularFile(c)) {
				return c.toAbsolutePath();
			}
		}
		throw new IOException("LinkCheck.java not found; run from the repository root or pass its path as argument");
	}

	// ------------------------------------------------------------------------------------------------
	// synthetic sources
	// ------------------------------------------------------------------------------------------------

	/** What the consumers are compiled against (the "old" or "expected" API). */
	private static Map<String, String> apiV2() {
		Map<String, String> s = new LinkedHashMap<>();
		s.put("tp/Base.java", """
				package tp;
				public class Base {
					public int baseField;
					public static int baseStatic;
					public void inheritedMethod() {}
					public static void baseStaticMethod() {}
				}
				""");
		s.put("tp/SuperApi.java", """
				package tp;
				public interface SuperApi {
					Object LOCK = new Object();
					void superApiMethod();
				}
				""");
		s.put("tp/Api.java", """
				package tp;
				public interface Api extends SuperApi {
					void apiMethod();
				}
				""");
		s.put("tp/AbstractImpl.java", """
				package tp;
				public abstract class AbstractImpl implements Api {}
				""");
		s.put("tp/Impl.java", """
				package tp;
				public class Impl extends Base implements Api {
					public int field;
					public static int staticField;
					public void instMethod() {}
					public static void staticMethod() {}
					public void descMethod(int x) {}
					public void apiMethod() {}
					public void superApiMethod() {}
					public void removedMethod() {}
					public int removedField;
					public void flipToStatic() {}
					public static void flipToInstance() {}
					public void becamePrivate() {}
					public int becamePrivateField;
					public int fieldToStatic;
					public static int fieldToInstance;
					public void pkgMethod() {}
					public void protMethod() {}
				}
				""");
		s.put("tp/Third.java", """
				package tp;
				public class Third extends Impl {}
				""");
		s.put("tp/ExtMc.java", """
				package tp;
				public class ExtMc extends ext.Outside {
					public void ownMethod() {}
				}
				""");
		s.put("tp/ListLike.java", """
				package tp;
				public class ListLike extends java.util.ArrayList<String> {
					public void gone() {}
				}
				""");
		s.put("tp/Gone.java", """
				package tp;
				public class Gone {
					public static void hi() {}
				}
				""");
		s.put("tp/ClassNowIface.java", """
				package tp;
				public class ClassNowIface {
					public void m() {}
				}
				""");
		s.put("tp/IfaceNowClass.java", """
				package tp;
				public interface IfaceNowClass {
					void m();
				}
				""");
		return s;
	}

	/** What the provider jar really contains: some members removed, changed or hidden. */
	private static Map<String, String> providerV1() {
		Map<String, String> s = apiV2();
		s.remove("tp/Gone.java");
		s.put("tp/Impl.java", """
				package tp;
				public class Impl extends Base implements Api {
					public int field;
					public static int staticField;
					public void instMethod() {}
					public static void staticMethod() {}
					public void descMethod(long x) {}
					public void apiMethod() {}
					public void superApiMethod() {}
					public static void flipToStatic() {}
					public void flipToInstance() {}
					private void becamePrivate() {}
					private int becamePrivateField;
					public static int fieldToStatic;
					public int fieldToInstance;
					void pkgMethod() {}
					protected void protMethod() {}
				}
				""");
		s.put("tp/ListLike.java", """
				package tp;
				public class ListLike extends java.util.ArrayList<String> {}
				""");
		s.put("tp/ClassNowIface.java", """
				package tp;
				public interface ClassNowIface {
					void m();
				}
				""");
		s.put("tp/IfaceNowClass.java", """
				package tp;
				public class IfaceNowClass {
					public void m() {}
				}
				""");
		return s;
	}

	private static Map<String, String> outsideSources() {
		Map<String, String> s = new LinkedHashMap<>();
		s.put("ext/Outside.java", """
				package ext;
				public class Outside {
					public void outsideMethod() {}
				}
				""");
		return s;
	}

	private static Map<String, String> consumerSources() {
		Map<String, String> s = new LinkedHashMap<>();
		s.put("cons/OkUser.java", """
				package cons;
				import tp.*;
				public class OkUser {
					void run(Impl i, Third t, AbstractImpl ai, Api api, ExtMc mc, ListLike ll) {
						int f = i.field;
						int sf = Impl.staticField;
						i.instMethod();
						Impl.staticMethod();
						t.inheritedMethod();
						Third.baseStaticMethod();
						int bf = t.baseField;
						int bs = Third.baseStatic;
						int size = ll.size();
						ll.stream();
						ai.superApiMethod();
						ai.apiMethod();
						Object lock = AbstractImpl.LOCK;
						api.superApiMethod();
						api.apiMethod();
						i.pkgMethod();
						i.protMethod();
						mc.ownMethod();
						mc.outsideMethod();
					}
				}
				""");
		s.put("cons/BadUser.java", """
				package cons;
				import tp.*;
				public class BadUser {
					void run(Impl i, ClassNowIface c, IfaceNowClass k, ListLike ll) {
						ll.gone();
						Gone.hi();
						i.removedMethod();
						int a = i.removedField;
						i.descMethod(1);
						i.flipToStatic();
						Impl.flipToInstance();
						i.becamePrivate();
						int b = i.becamePrivateField;
						int c1 = i.fieldToStatic;
						int c2 = Impl.fieldToInstance;
						c.m();
						k.m();
					}
				}
				""");
		s.put("cons/NestedUser.java", """
				package cons;
				import tp.*;
				public class NestedUser {
					void run(Impl i) {
						i.removedMethod();
						int f = i.field;
					}
				}
				""");
		s.put("cons/NoRef.java", """
				package cons;
				public class NoRef {
					void run() {
						System.out.println("nothing from the provider");
					}
				}
				""");
		s.put("cons/SelfUser.java", """
				package cons;
				import tp.*;
				public class SelfUser {
					void run(Impl i) {
						Own.run();
						int f = i.field;
					}
				}
				""");
		return s;
	}

	private static Map<String, String> ownSources() {
		Map<String, String> s = new LinkedHashMap<>();
		s.put("tp/Own.java", """
				package tp;
				public class Own {
					public static void run() {}
				}
				""");
		return s;
	}

	// ------------------------------------------------------------------------------------------------
	// scenarios
	// ------------------------------------------------------------------------------------------------

	private static void runAll(Path temp) throws Exception {
		Path outsideClasses = compile(temp, "outside", outsideSources(), "");
		Path v2Classes = compile(temp, "v2", apiV2(), outsideClasses.toString());
		Path v1Classes = compile(temp, "v1", providerV1(), outsideClasses.toString());
		String api = v2Classes + java.io.File.pathSeparator + outsideClasses;
		Path ownClasses = compile(temp, "own", ownSources(), api);
		Path consClasses = compile(temp, "cons", consumerSources(), api + java.io.File.pathSeparator + ownClasses);

		Path providerJar = temp.resolve("provider.jar");
		writeJar(providerJar, classesOf(v1Classes, n -> true));

		Path okJar = temp.resolve("ok-consumer.jar");
		writeJar(okJar, classesOf(consClasses, n -> n.equals("cons/OkUser.class")));
		Path badJar = temp.resolve("bad-consumer.jar");
		writeJar(badJar, classesOf(consClasses, n -> n.equals("cons/BadUser.class")));

		Map<String, byte[]> nestedInner = classesOf(consClasses, n -> n.equals("cons/NestedUser.class"));
		byte[] innerJar = jarBytes(nestedInner);
		Path nestedA = temp.resolve("nested-a.jar");
		Map<String, byte[]> a = new LinkedHashMap<>();
		a.put("META-INF/jars/inner.jar", innerJar);
		writeJar(nestedA, a);
		Path nestedB = temp.resolve("nested-b.jar");
		Map<String, byte[]> midEntries = new LinkedHashMap<>();
		midEntries.put("META-INF/jars/inner.jar", innerJar);
		Map<String, byte[]> b = new LinkedHashMap<>();
		b.put("META-INF/jarjar/mid.jar", jarBytes(midEntries));
		writeJar(nestedB, b);

		Path noRefJar = temp.resolve("noref-consumer.jar");
		writeJar(noRefJar, classesOf(consClasses, n -> n.equals("cons/NoRef.class")));

		Map<String, byte[]> selfEntries = classesOf(consClasses, n -> n.equals("cons/SelfUser.class"));
		selfEntries.putAll(classesOf(ownClasses, n -> true));
		Path selfJar = temp.resolve("self-consumer.jar");
		writeJar(selfJar, selfEntries);

		System.out.println("== (a)(c) everything resolvable: direct, inherited from superclass and from super-interface, through JDK classes");
		Path report1 = temp.resolve("report1.txt");
		Result r1 = linkCheck("--provider", providerJar.toString(), "--consumer", okJar.toString(), "--prefix", "tp.",
				"--verbose", "--report", report1.toString());
		expect("ok run exits 0", r1.exit == 0);
		Map<String, Map<String, List<String>>> p1 = parse(r1.out);
		Map<String, List<String>> ok = p1.get("ok-consumer.jar");
		expect("ok run has a consumer section", ok != null);
		if (ok != null) {
			expect("ok run: MISSING (0)", count(ok, "MISSING") == 0);
			expect("ok run: ERRORS (0)", count(ok, "ERRORS") == 0);
			expect("ok run: UNVERIFIABLE (1)", count(ok, "UNVERIFIABLE") == 1);
			expect("ok run: WARNINGS (2) for package-private and protected",
					count(ok, "WARNINGS") == 2 && hasLine(ok, "WARNINGS", "tp.Impl.pkgMethod()V") && hasLine(ok, "WARNINGS", "tp.Impl.protMethod()V"));
			expect("ok run: unverifiable names the boundary class",
					hasLine(ok, "UNVERIFIABLE", "tp.ExtMc.outsideMethod()V") && joined(ok, "UNVERIFIABLE").contains("hierarchy leaves the provider at ext.Outside"));
			Map<String, String> status = statusMap(ok);
			String[] okRefs = {
					"tp.Impl.field:I", "tp.Impl.staticField:I", "tp.Impl.instMethod()V", "tp.Impl.staticMethod()V",
					"tp.Third.inheritedMethod()V", "tp.Third.baseStaticMethod()V", "tp.Third.baseField:I", "tp.Third.baseStatic:I",
					"tp.ListLike.size()I", "tp.ListLike.stream()Ljava/util/stream/Stream;", "tp.AbstractImpl.superApiMethod()V", "tp.AbstractImpl.apiMethod()V",
					"tp.AbstractImpl.LOCK:Ljava/lang/Object;", "tp.Api.superApiMethod()V", "tp.Api.apiMethod()V",
					"tp.ExtMc.ownMethod()V", "tp.Impl", "tp.Third", "tp.AbstractImpl", "tp.Api" };
			for (String ref : okRefs) {
				expect("ok run: " + ref + " is OK", "OK".equals(status.get(ref)));
			}
		}
		String fileText = Files.readString(report1, StandardCharsets.UTF_8);
		expect("--report file equals the printed report", fileText.equals(r1.out));

		System.out.println("== (b) missing class, method, field, other descriptor, static mismatch, private, Methodref/InterfaceMethodref kind");
		Result r2 = linkCheck("--provider", providerJar.toString(), "--consumer", badJar.toString(), "--prefix", "tp/");
		expect("bad run exits 1", r2.exit == 1);
		Map<String, List<String>> bad = parse(r2.out).get("bad-consumer.jar");
		expect("bad run has a consumer section", bad != null);
		if (bad != null) {
			expect("bad run: MISSING (6)", count(bad, "MISSING") == 6);
			expect("bad run: ERRORS (8)", count(bad, "ERRORS") == 8);
			expect("bad run: UNVERIFIABLE (0)", count(bad, "UNVERIFIABLE") == 0);
			expect("missing class reported", hasLine(bad, "MISSING", "tp.Gone  (used by cons.BadUser)"));
			expect("method of missing class reported", hasLine(bad, "MISSING", "tp.Gone.hi()V"));
			expect("missing method reported", hasLine(bad, "MISSING", "tp.Impl.removedMethod()V"));
			expect("method missing in a hierarchy that ends in JDK classes is MISSING, not unverifiable", hasLine(bad, "MISSING", "tp.ListLike.gone()V"));
			expect("missing field reported", hasLine(bad, "MISSING", "tp.Impl.removedField:I"));
			expect("different descriptor reported with hint",
					hasLine(bad, "MISSING", "tp.Impl.descMethod(I)V") && joined(bad, "MISSING").contains("exists with descriptor (J)V"));
			expect("used-by is printed", joined(bad, "MISSING").contains("(used by cons.BadUser)"));
			expect("invokevirtual on static method", hasLine(bad, "ERRORS", "tp.Impl.flipToStatic()V") && lineWith(bad, "ERRORS", "tp.Impl.flipToStatic()V").contains("on a static method"));
			expect("invokestatic on instance method", hasLine(bad, "ERRORS", "tp.Impl.flipToInstance()V") && lineWith(bad, "ERRORS", "tp.Impl.flipToInstance()V").contains("on a non-static method"));
			expect("getfield on static field", hasLine(bad, "ERRORS", "tp.Impl.fieldToStatic:I") && lineWith(bad, "ERRORS", "tp.Impl.fieldToStatic:I").contains("on a static field"));
			expect("getstatic on instance field", hasLine(bad, "ERRORS", "tp.Impl.fieldToInstance:I") && lineWith(bad, "ERRORS", "tp.Impl.fieldToInstance:I").contains("on a non-static field"));
			expect("private method", hasLine(bad, "ERRORS", "tp.Impl.becamePrivate()V") && lineWith(bad, "ERRORS", "tp.Impl.becamePrivate()V").contains("private"));
			expect("private field", hasLine(bad, "ERRORS", "tp.Impl.becamePrivateField:I") && lineWith(bad, "ERRORS", "tp.Impl.becamePrivateField:I").contains("private"));
			expect("Methodref on an interface", hasLine(bad, "ERRORS", "tp.ClassNowIface.m()V") && lineWith(bad, "ERRORS", "tp.ClassNowIface.m()V").contains("Methodref on an interface"));
			expect("InterfaceMethodref on a class", hasLine(bad, "ERRORS", "tp.IfaceNowClass.m()V") && lineWith(bad, "ERRORS", "tp.IfaceNowClass.m()V").contains("InterfaceMethodref on a class"));
		}

		System.out.println("== (d) consumer classes in nested jars (META-INF/jars, META-INF/jarjar two levels deep) and a consumer without references");
		Result r3 = linkCheck("--provider", providerJar.toString(), "--consumer", nestedA.toString(), "--consumer", nestedB.toString(),
				"--consumer", noRefJar.toString(), "--prefix", "tp/");
		expect("nested run exits 1", r3.exit == 1);
		Map<String, Map<String, List<String>>> p3 = parse(r3.out);
		for (String name : new String[] { "nested-a.jar", "nested-b.jar" }) {
			Map<String, List<String>> sec = p3.get(name);
			expect(name + ": section present", sec != null);
			if (sec != null) {
				expect(name + ": nested class scanned, missing method found", count(sec, "MISSING") == 1 && hasLine(sec, "MISSING", "tp.Impl.removedMethod()V"));
				expect(name + ": used-by names the nested class", joined(sec, "MISSING").contains("cons.NestedUser"));
				expect(name + ": nothing else wrong", count(sec, "ERRORS") == 0 && count(sec, "UNVERIFIABLE") == 0);
			}
		}
		Map<String, List<String>> noRef = p3.get("noref-consumer.jar");
		expect("no-reference consumer is not an error and prints the note",
				noRef != null && count(noRef, "MISSING") == 0 && r3.out.contains("0 references with prefix tp/"));

		System.out.println("== consumer's own classes are skipped unless --include-self");
		Result r4 = linkCheck("--provider", providerJar.toString(), "--consumer", selfJar.toString(), "--prefix", "tp/");
		expect("own classes skipped: exit 0", r4.exit == 0);
		Result r5 = linkCheck("--provider", providerJar.toString(), "--consumer", selfJar.toString(), "--prefix", "tp/", "--include-self");
		expect("--include-self: exit 1", r5.exit == 1);
		Map<String, List<String>> self = parse(r5.out).get("self-consumer.jar");
		expect("--include-self: own class required from provider", self != null && hasLine(self, "MISSING", "tp.Own") && hasLine(self, "MISSING", "tp.Own.run()V"));

		System.out.println("== default prefix and usage errors");
		Result r6 = linkCheck("--provider", providerJar.toString(), "--consumer", okJar.toString());
		expect("default prefix xaero/pac/ finds no references in the synthetic consumer, exit 0",
				r6.exit == 0 && r6.out.contains("0 references with prefix xaero/pac/"));
		expect("no arguments: exit 2", linkCheck().exit == 2);
		expect("missing consumer file: exit 2", linkCheck("--provider", providerJar.toString(), "--consumer", temp.resolve("nope.jar").toString()).exit == 2);
		expect("unknown option: exit 2", linkCheck("--provider", providerJar.toString(), "--consumer", okJar.toString(), "--bogus").exit == 2);
		Path notAJar = temp.resolve("not-a.jar");
		Files.writeString(notAJar, "this is not a zip file");
		expect("corrupt consumer jar: exit 2", linkCheck("--provider", providerJar.toString(), "--consumer", notAJar.toString()).exit == 2);
	}

	// ------------------------------------------------------------------------------------------------
	// helpers: process, report parsing, assertions
	// ------------------------------------------------------------------------------------------------

	private record Result(int exit, String out) { }

	private static Result linkCheck(String... args) throws IOException, InterruptedException {
		Path javaBin = Paths.get(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
		List<String> cmd = new ArrayList<>();
		cmd.add(javaBin.toString());
		cmd.add("-XX:TieredStopAtLevel=1");
		cmd.add(linkCheck.toString());
		cmd.addAll(List.of(args));
		Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		byte[] bytes = p.getInputStream().readAllBytes();
		int exit = p.waitFor();
		return new Result(exit, new String(bytes, StandardCharsets.UTF_8));
	}

	private static final Pattern CONSUMER_HEADER = Pattern.compile("^=== consumer: (.*) ===$");
	private static final Pattern SECTION_HEADER = Pattern.compile("^([A-Z ]+) \\((\\d+)\\):$");

	/** consumer file name to (section title to lines; "title#count" to the announced count). */
	private static Map<String, Map<String, List<String>>> parse(String text) {
		Map<String, Map<String, List<String>>> result = new HashMap<>();
		Map<String, List<String>> current = null;
		List<String> section = null;
		for (String line : text.split("\n", -1)) {
			Matcher m = CONSUMER_HEADER.matcher(line);
			if (m.matches()) {
				current = new HashMap<>();
				result.put(m.group(1), current);
				section = null;
				continue;
			}
			if (current == null) {
				continue;
			}
			Matcher s = SECTION_HEADER.matcher(line);
			if (s.matches()) {
				section = new ArrayList<>();
				current.put(s.group(1), section);
				current.put(s.group(1) + "#count", List.of(s.group(2)));
			} else if (section != null && line.startsWith("  ")) {
				section.add(line.substring(2));
			}
		}
		return result;
	}

	private static int count(Map<String, List<String>> sections, String title) {
		List<String> c = sections.get(title + "#count");
		List<String> lines = sections.get(title);
		if (c == null || lines == null || Integer.parseInt(c.get(0)) != lines.size()) {
			return -1;
		}
		return lines.size();
	}

	private static boolean hasLine(Map<String, List<String>> sections, String title, String start) {
		return lineWith(sections, title, start) != null;
	}

	private static String lineWith(Map<String, List<String>> sections, String title, String start) {
		for (String l : sections.getOrDefault(title, List.of())) {
			if (l.equals(start) || l.startsWith(start + "  (")) {
				return l;
			}
			if (start.contains("(used by") && l.startsWith(start)) {
				return l;
			}
		}
		return null;
	}

	private static String joined(Map<String, List<String>> sections, String title) {
		return String.join("\n", sections.getOrDefault(title, List.of()));
	}

	/** Reference text to status from the --verbose list. */
	private static Map<String, String> statusMap(Map<String, List<String>> sections) {
		Map<String, String> map = new TreeMap<>();
		for (String l : sections.getOrDefault("ALL CHECKED REFERENCES", List.of())) {
			int sp = l.indexOf(' ');
			String status = l.substring(0, sp);
			String rest = l.substring(sp).stripLeading();
			int end = rest.indexOf("  (");
			map.put(end < 0 ? rest : rest.substring(0, end), status);
		}
		return map;
	}

	private static void expect(String what, boolean condition) {
		if (condition) {
			passed++;
			System.out.println("  ok   " + what);
		} else {
			failures.add(what);
			System.out.println("  FAIL " + what);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// helpers: compile, jar, files
	// ------------------------------------------------------------------------------------------------

	private static Path compile(Path temp, String name, Map<String, String> sources, String classpath) throws IOException {
		Path srcDir = temp.resolve(name + "-src");
		Path outDir = temp.resolve(name + "-classes");
		Files.createDirectories(outDir);
		List<String> args = new ArrayList<>(List.of("-d", outDir.toString(), "-proc:none", "-nowarn", "-Xlint:none"));
		if (!classpath.isEmpty()) {
			args.add("-cp");
			args.add(classpath);
		}
		for (Map.Entry<String, String> e : sources.entrySet()) {
			Path file = srcDir.resolve(e.getKey());
			Files.createDirectories(file.getParent());
			Files.writeString(file, e.getValue(), StandardCharsets.UTF_8);
			args.add(file.toString());
		}
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new IOException("no system Java compiler (run with a JDK, not a JRE)");
		}
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int rc = compiler.run(null, null, err, args.toArray(new String[0]));
		if (rc != 0) {
			throw new IOException("compilation of " + name + " failed:\n" + err.toString(StandardCharsets.UTF_8));
		}
		return outDir;
	}

	private static Map<String, byte[]> classesOf(Path dir, Predicate<String> filter) throws IOException {
		Map<String, byte[]> result = new LinkedHashMap<>();
		try (Stream<Path> files = Files.walk(dir)) {
			for (Path p : (Iterable<Path>) files.sorted()::iterator) {
				if (Files.isRegularFile(p) && p.toString().endsWith(".class")) {
					String name = dir.relativize(p).toString().replace('\\', '/');
					if (filter.test(name)) {
						result.put(name, Files.readAllBytes(p));
					}
				}
			}
		}
		return result;
	}

	private static byte[] jarBytes(Map<String, byte[]> entries) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (JarOutputStream jos = new JarOutputStream(bos)) {
			for (Map.Entry<String, byte[]> e : entries.entrySet()) {
				jos.putNextEntry(new JarEntry(e.getKey()));
				jos.write(e.getValue());
				jos.closeEntry();
			}
		}
		return bos.toByteArray();
	}

	private static void writeJar(Path jar, Map<String, byte[]> entries) throws IOException {
		Files.write(jar, jarBytes(entries));
	}

	private static void deleteRecursively(Path dir) {
		try {
			Files.walkFileTree(dir, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.deleteIfExists(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
					Files.deleteIfExists(d);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
