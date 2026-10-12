import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Linkage check between a provider jar and consumer jars.
 *
 * Verifies that every class, method and field below a package prefix that the consumer jars reference
 * (constant pool of every class, nested jars included) exists in the provider jar the way the JVM would
 * link it. Single file, no dependencies: java tools/linkcheck/LinkCheck.java --provider P.jar --consumer C.jar
 */
public final class LinkCheck {

	private static final int ACC_PUBLIC = 0x0001;
	private static final int ACC_PRIVATE = 0x0002;
	private static final int ACC_PROTECTED = 0x0004;
	private static final int ACC_STATIC = 0x0008;
	private static final int ACC_INTERFACE = 0x0200;

	private static final int TAG_UTF8 = 1;
	private static final int TAG_CLASS = 7;
	private static final int TAG_FIELDREF = 9;
	private static final int TAG_METHODREF = 10;
	private static final int TAG_IMETHODREF = 11;
	private static final int TAG_METHOD_HANDLE = 15;

	private static final int MAX_NESTING = 16;
	private static final int MAX_USERS_SHOWN = 3;

	private static final String NL = "\n";

	public static void main(String[] args) {
		PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
		PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
		int code;
		try {
			code = run(args, out, err);
		} catch (IOException e) {
			err.println("I/O error: " + e.getMessage());
			code = 2;
		}
		out.flush();
		err.flush();
		System.exit(code);
	}

	// ------------------------------------------------------------------------------------------------
	// command line
	// ------------------------------------------------------------------------------------------------

	private static final class Options {
		Path provider;
		final List<Path> consumers = new ArrayList<>();
		String prefix = "xaero/pac/";
		Path report;
		boolean verbose;
		boolean includeSelf;
	}

	private static final String USAGE = String.join(NL,
			"Usage: java LinkCheck.java --provider <jar> --consumer <jar> [--consumer <jar> ...]",
			"                           [--prefix xaero/pac/] [--report <file>] [--verbose] [--include-self]",
			"",
			"  --provider <jar>   jar that must provide the referenced classes, methods and fields",
			"  --consumer <jar>   jar whose references are checked (repeatable); nested jars are scanned too",
			"  --prefix <p>       only references whose owner class starts with this prefix (default xaero/pac/;",
			"                     dots are accepted: xaero.pac.)",
			"  --report <file>    also write the report to this file (UTF-8)",
			"  --verbose          list every checked reference, not only problems",
			"  --include-self     also require classes the consumer defines itself (default: they are skipped)",
			"",
			"Exit codes: 0 = nothing missing, 1 = missing/incompatible references, 2 = usage or I/O problem");

	private static Options parseArgs(String[] args) {
		Options o = new Options();
		for (int i = 0; i < args.length; i++) {
			String a = args[i];
			switch (a) {
				case "--provider" -> o.provider = Paths.get(value(args, ++i, a));
				case "--consumer" -> o.consumers.add(Paths.get(value(args, ++i, a)));
				case "--prefix" -> o.prefix = value(args, ++i, a).replace('.', '/');
				case "--report" -> o.report = Paths.get(value(args, ++i, a));
				case "--verbose" -> o.verbose = true;
				case "--include-self" -> o.includeSelf = true;
				default -> throw new IllegalArgumentException("unknown argument: " + a);
			}
		}
		if (o.provider == null) {
			throw new IllegalArgumentException("--provider is required");
		}
		if (o.consumers.isEmpty()) {
			throw new IllegalArgumentException("at least one --consumer is required");
		}
		if (o.prefix.isEmpty()) {
			throw new IllegalArgumentException("--prefix must not be empty");
		}
		return o;
	}

	private static String value(String[] args, int index, String option) {
		if (index >= args.length) {
			throw new IllegalArgumentException(option + " needs a value");
		}
		return args[index];
	}

	static int run(String[] args, PrintStream out, PrintStream err) throws IOException {
		for (String a : args) {
			if (a.equals("--help") || a.equals("-h")) {
				out.println(USAGE);
				return 0;
			}
		}
		Options opt;
		try {
			opt = parseArgs(args);
		} catch (IllegalArgumentException e) {
			err.println("error: " + e.getMessage());
			err.println(USAGE);
			return 2;
		}
		if (!Files.isRegularFile(opt.provider)) {
			err.println("error: provider jar not found: " + opt.provider);
			return 2;
		}
		for (Path c : opt.consumers) {
			if (!Files.isRegularFile(c)) {
				err.println("error: consumer jar not found: " + c);
				return 2;
			}
		}

		StringBuilder rep = new StringBuilder();
		Map<String, ClassInfo> provider = new HashMap<>();
		List<String> providerWarnings = new ArrayList<>();
		scanJar(opt.provider, (location, data) -> {
			if (location.contains("META-INF/versions/") || location.endsWith("module-info.class")) {
				return;
			}
			try {
				ClassInfo ci = ClassInfo.parse(data, false);
				provider.putIfAbsent(ci.name, ci);
			} catch (IOException | RuntimeException e) {
				providerWarnings.add("unreadable provider class " + location + ": " + e.getMessage());
			}
		}, providerWarnings);
		if (provider.isEmpty()) {
			err.println("error: provider jar contains no readable classes: " + opt.provider);
			return 2;
		}

		rep.append("LinkCheck report").append(NL);
		rep.append("provider: ").append(opt.provider).append(" (").append(provider.size()).append(" classes)").append(NL);
		rep.append("prefix: ").append(opt.prefix).append(NL);
		if (opt.includeSelf) {
			rep.append("mode: --include-self (classes defined by the consumer are required from the provider too)").append(NL);
		}
		for (String w : providerWarnings) {
			rep.append("WARNING: ").append(w).append(NL);
		}

		Checker checker = new Checker(provider);
		int problems = 0;
		for (Path consumerJar : opt.consumers) {
			ConsumerScan scan = scanConsumer(consumerJar, opt.prefix);
			problems += report(rep, consumerJar, scan, checker, opt);
		}
		rep.append(NL);
		rep.append(problems == 0 ? "RESULT: OK" : "RESULT: FAIL (" + problems + " missing or incompatible references)").append(NL);

		String text = rep.toString();
		out.print(text);
		if (opt.report != null) {
			Path parent = opt.report.toAbsolutePath().getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.write(opt.report, text.getBytes(StandardCharsets.UTF_8));
		}
		return problems == 0 ? 0 : 1;
	}

	// ------------------------------------------------------------------------------------------------
	// jar reading
	// ------------------------------------------------------------------------------------------------

	private interface ClassSink {
		void accept(String location, byte[] data);
	}

	private static void scanJar(Path jar, ClassSink sink, List<String> warnings) throws IOException {
		try (ZipFile zf = new ZipFile(jar.toFile())) {
			Enumeration<? extends ZipEntry> entries = zf.entries();
			while (entries.hasMoreElements()) {
				ZipEntry ze = entries.nextElement();
				if (ze.isDirectory()) {
					continue;
				}
				String name = ze.getName();
				String lower = name.toLowerCase(Locale.ROOT);
				try {
					if (lower.endsWith(".class")) {
						try (InputStream in = zf.getInputStream(ze)) {
							sink.accept(name, in.readAllBytes());
						}
					} else if (lower.endsWith(".jar")) {
						try (InputStream in = zf.getInputStream(ze)) {
							scanStream(in, name, 1, sink, warnings);
						}
					}
				} catch (IOException e) {
					warnings.add("cannot read entry " + name + ": " + e.getMessage());
				}
			}
		}
	}

	private static void scanStream(InputStream in, String label, int depth, ClassSink sink, List<String> warnings) {
		if (depth > MAX_NESTING) {
			warnings.add("nested jar too deep, skipped: " + label);
			return;
		}
		try (ZipInputStream zin = new ZipInputStream(in)) {
			ZipEntry ze;
			while ((ze = zin.getNextEntry()) != null) {
				if (ze.isDirectory()) {
					continue;
				}
				String name = ze.getName();
				String lower = name.toLowerCase(Locale.ROOT);
				if (lower.endsWith(".class")) {
					sink.accept(label + "!/" + name, zin.readAllBytes());
				} else if (lower.endsWith(".jar")) {
					byte[] inner = zin.readAllBytes();
					scanStream(new ByteArrayInputStream(inner), label + "!/" + name, depth + 1, sink, warnings);
				}
			}
		} catch (IOException e) {
			warnings.add("cannot read nested jar " + label + ": " + e.getMessage());
		}
	}

	// ------------------------------------------------------------------------------------------------
	// consumer scan
	// ------------------------------------------------------------------------------------------------

	private static class Agg {
		int userCount;
		final List<String> users = new ArrayList<>();
		boolean otherPkgUse;
		boolean nonNestUse;

		void use(String userClass, String ownerClass) {
			userCount++;
			if (users.size() < MAX_USERS_SHOWN) {
				users.add(userClass.replace('/', '.'));
			}
			if (!packageOf(userClass).equals(packageOf(ownerClass))) {
				otherPkgUse = true;
			}
			if (!nestOf(userClass).equals(nestOf(ownerClass))) {
				nonNestUse = true;
			}
		}

		String usedBy() {
			StringBuilder sb = new StringBuilder("used by ");
			for (int i = 0; i < users.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append(users.get(i));
			}
			if (userCount > users.size()) {
				sb.append(", +").append(userCount - users.size()).append(" more");
			}
			return sb.toString();
		}
	}

	private static final class ClassAgg extends Agg {
		final String name;

		ClassAgg(String name) {
			this.name = name;
		}
	}

	private static final class RefAgg extends Agg {
		final int tag;
		final String owner;
		final String name;
		final String desc;
		boolean usedStatic;
		boolean usedNonStatic;

		RefAgg(int tag, String owner, String name, String desc) {
			this.tag = tag;
			this.owner = owner;
			this.name = name;
			this.desc = desc;
		}

		boolean isField() {
			return tag == TAG_FIELDREF;
		}

		String display() {
			return owner.replace('/', '.') + "." + name + (isField() ? ":" : "") + desc;
		}
	}

	private static final class ConsumerScan {
		final Set<String> ownClasses = new HashSet<>();
		final Map<String, ClassAgg> classes = new TreeMap<>();
		final Map<String, RefAgg> refs = new TreeMap<>();
		final List<String> warnings = new ArrayList<>();
		int classesScanned;
		int walkFailures;
	}

	private static ConsumerScan scanConsumer(Path jar, String prefix) throws IOException {
		ConsumerScan scan = new ConsumerScan();
		scanJar(jar, (location, data) -> {
			if (location.endsWith("module-info.class")) {
				return;
			}
			try {
				ClassInfo ci = ClassInfo.parse(data, true);
				scan.classesScanned++;
				scan.ownClasses.add(ci.name);
				if (ci.walkFailures > 0) {
					scan.walkFailures += ci.walkFailures;
					for (String m : ci.walkMessages) {
						scan.warnings.add("bytecode walk failed (static/non-static usage unknown): " + m);
					}
				}
				extract(ci, prefix, scan);
			} catch (IOException | RuntimeException e) {
				scan.warnings.add("unreadable consumer class " + location + ": " + e.getMessage());
			}
		}, scan.warnings);
		return scan;
	}

	private static void extract(ClassInfo ci, String prefix, ConsumerScan scan) throws IOException {
		int n = ci.tags.length;
		boolean[] st = ci.st;
		boolean[] ns = ci.ns;
		// method handle constants also tell how a member is used
		for (int i = 1; i < n; i++) {
			if (ci.tags[i] == TAG_METHOD_HANDLE) {
				int kind = ci.a[i];
				int ref = ci.b[i];
				if (ref > 0 && ref < n) {
					if (kind == 2 || kind == 4 || kind == 6) {
						st[ref] = true;
					} else {
						ns[ref] = true;
					}
				}
			}
		}
		Set<String> seen = new HashSet<>();
		for (int i = 1; i < n; i++) {
			int tag = ci.tags[i];
			if (tag == TAG_CLASS) {
				String elem = elementClass(ClassInfo.utf(ci, ci.a[i]));
				if (elem != null && elem.startsWith(prefix) && seen.add("C" + elem)) {
					scan.classes.computeIfAbsent(elem, ClassAgg::new).use(ci.name, elem);
				}
			} else if (tag == TAG_FIELDREF || tag == TAG_METHODREF || tag == TAG_IMETHODREF) {
				String owner = ClassInfo.utf(ci, ci.a[ci.a[i]]);
				if (!owner.startsWith(prefix)) {
					continue;
				}
				int nat = ci.b[i];
				String name = ClassInfo.utf(ci, ci.a[nat]);
				String desc = ClassInfo.utf(ci, ci.b[nat]);
				String key = tag + "|" + owner + "|" + name + "|" + desc;
				RefAgg agg = scan.refs.computeIfAbsent(key, k -> new RefAgg(tag, owner, name, desc));
				if (seen.add(key)) {
					agg.use(ci.name, owner);
				}
				agg.usedStatic |= st[i];
				agg.usedNonStatic |= ns[i];
			}
		}
	}

	/** Element class of a CONSTANT_Class name (array types resolved), or null for primitive arrays. */
	private static String elementClass(String name) {
		if (name.startsWith("[")) {
			int i = 0;
			while (i < name.length() && name.charAt(i) == '[') {
				i++;
			}
			if (i < name.length() && name.charAt(i) == 'L' && name.endsWith(";")) {
				return name.substring(i + 1, name.length() - 1);
			}
			return null;
		}
		return name;
	}

	private static String packageOf(String internalName) {
		int i = internalName.lastIndexOf('/');
		return i < 0 ? "" : internalName.substring(0, i);
	}

	private static String nestOf(String internalName) {
		int d = internalName.indexOf('$', internalName.lastIndexOf('/') + 1);
		return d < 0 ? internalName : internalName.substring(0, d);
	}

	// ------------------------------------------------------------------------------------------------
	// class file model and parser
	// ------------------------------------------------------------------------------------------------

	static final class ClassInfo {
		String name;
		String superName;
		String[] interfaces;
		int access;
		final Map<String, Integer> fields = new HashMap<>();
		final Map<String, Integer> methods = new HashMap<>();

		// only filled in deep mode (consumer classes)
		byte[] tags;
		String[] utf;
		int[] a;
		int[] b;
		boolean[] st;
		boolean[] ns;
		int walkFailures;
		final List<String> walkMessages = new ArrayList<>();

		static String utf(ClassInfo ci, int idx) throws IOException {
			return utf(ci.utf, idx);
		}

		static String utf(String[] utf, int idx) throws IOException {
			if (idx <= 0 || idx >= utf.length || utf[idx] == null) {
				throw new IOException("bad constant pool utf8 index " + idx);
			}
			return utf[idx];
		}

		static ClassInfo parse(byte[] data, boolean deep) throws IOException {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
			if (in.readInt() != 0xCAFEBABE) {
				throw new IOException("not a class file (bad magic)");
			}
			in.readUnsignedShort();
			in.readUnsignedShort();
			int n = in.readUnsignedShort();
			byte[] tags = new byte[n];
			String[] utf = new String[n];
			int[] a = new int[n];
			int[] b = new int[n];
			for (int i = 1; i < n; i++) {
				int t = in.readUnsignedByte();
				tags[i] = (byte) t;
				switch (t) {
					case TAG_UTF8 -> utf[i] = in.readUTF();
					case 3, 4 -> in.skipNBytes(4);
					case 5, 6 -> {
						in.skipNBytes(8);
						i++;
					}
					case TAG_CLASS -> a[i] = in.readUnsignedShort();
					case 8, 16, 19, 20 -> in.skipNBytes(2);
					case TAG_FIELDREF, TAG_METHODREF, TAG_IMETHODREF, 12, 17, 18 -> {
						a[i] = in.readUnsignedShort();
						b[i] = in.readUnsignedShort();
					}
					case TAG_METHOD_HANDLE -> {
						a[i] = in.readUnsignedByte();
						b[i] = in.readUnsignedShort();
					}
					default -> throw new IOException("unknown constant pool tag " + t + " at index " + i);
				}
			}
			ClassInfo ci = new ClassInfo();
			ci.access = in.readUnsignedShort();
			ci.name = utf(utf, a[in.readUnsignedShort()]);
			int superIdx = in.readUnsignedShort();
			ci.superName = superIdx == 0 ? null : utf(utf, a[superIdx]);
			int ic = in.readUnsignedShort();
			ci.interfaces = new String[ic];
			for (int i = 0; i < ic; i++) {
				ci.interfaces[i] = utf(utf, a[in.readUnsignedShort()]);
			}
			if (deep) {
				ci.tags = tags;
				ci.utf = utf;
				ci.a = a;
				ci.b = b;
				ci.st = new boolean[n];
				ci.ns = new boolean[n];
			}
			int fc = in.readUnsignedShort();
			for (int i = 0; i < fc; i++) {
				int acc = in.readUnsignedShort();
				String fname = utf(utf, in.readUnsignedShort());
				String fdesc = utf(utf, in.readUnsignedShort());
				ci.fields.put(fname + ":" + fdesc, acc);
				skipAttributes(in, utf, null, null);
			}
			int mc = in.readUnsignedShort();
			for (int i = 0; i < mc; i++) {
				int acc = in.readUnsignedShort();
				String mname = utf(utf, in.readUnsignedShort());
				String mdesc = utf(utf, in.readUnsignedShort());
				ci.methods.put(mname + mdesc, acc);
				skipAttributes(in, utf, deep ? ci : null, mname + mdesc);
			}
			return ci;
		}

		private static void skipAttributes(DataInputStream in, String[] utf, ClassInfo codeTarget, String method) throws IOException {
			int count = in.readUnsignedShort();
			for (int i = 0; i < count; i++) {
				String attr = utf(utf, in.readUnsignedShort());
				long len = in.readInt() & 0xFFFFFFFFL;
				if (len > in.available()) {
					throw new IOException("attribute " + attr + " longer than the class file");
				}
				if (codeTarget != null && attr.equals("Code")) {
					byte[] code = new byte[(int) len];
					in.readFully(code);
					try {
						walkCode(code, codeTarget);
					} catch (IOException | RuntimeException e) {
						codeTarget.walkFailures++;
						codeTarget.walkMessages.add(codeTarget.name.replace('/', '.') + "." + method + ": " + e.getMessage());
					}
				} else {
					in.skipNBytes(len);
				}
			}
		}
	}

	/** Instruction length including the opcode; 0 = variable (switches, wide); -1 = illegal. */
	private static final byte[] OPLEN = new byte[256];

	static {
		Arrays.fill(OPLEN, (byte) -1);
		fillLen(0x00, 0x0f, 1);
		fillLen(0x10, 0x10, 2);
		fillLen(0x11, 0x11, 3);
		fillLen(0x12, 0x12, 2);
		fillLen(0x13, 0x14, 3);
		fillLen(0x15, 0x19, 2);
		fillLen(0x1a, 0x35, 1);
		fillLen(0x36, 0x3a, 2);
		fillLen(0x3b, 0x83, 1);
		fillLen(0x84, 0x84, 3);
		fillLen(0x85, 0x98, 1);
		fillLen(0x99, 0xa8, 3);
		fillLen(0xa9, 0xa9, 2);
		fillLen(0xaa, 0xab, 0);
		fillLen(0xac, 0xb1, 1);
		fillLen(0xb2, 0xb8, 3);
		fillLen(0xb9, 0xba, 5);
		fillLen(0xbb, 0xbb, 3);
		fillLen(0xbc, 0xbc, 2);
		fillLen(0xbd, 0xbd, 3);
		fillLen(0xbe, 0xbf, 1);
		fillLen(0xc0, 0xc1, 3);
		fillLen(0xc2, 0xc3, 1);
		fillLen(0xc4, 0xc4, 0);
		fillLen(0xc5, 0xc5, 4);
		fillLen(0xc6, 0xc7, 3);
		fillLen(0xc8, 0xc9, 5);
	}

	private static void fillLen(int from, int to, int len) {
		for (int i = from; i <= to; i++) {
			OPLEN[i] = (byte) len;
		}
	}

	private static int u2(byte[] d, int p) {
		return ((d[p] & 0xff) << 8) | (d[p + 1] & 0xff);
	}

	private static int s4(byte[] d, int p) {
		return ((d[p] & 0xff) << 24) | ((d[p + 1] & 0xff) << 16) | ((d[p + 2] & 0xff) << 8) | (d[p + 3] & 0xff);
	}

	/** Walks the instructions of one Code attribute and records how field and method references are used. */
	private static void walkCode(byte[] attr, ClassInfo ci) throws IOException {
		if (attr.length < 8) {
			throw new IOException("Code attribute too short");
		}
		int codeLen = s4(attr, 4);
		int start = 8;
		long endL = (long) start + codeLen;
		if (codeLen < 0 || endL > attr.length) {
			throw new IOException("bad code length " + codeLen);
		}
		int end = (int) endL;
		int pc = start;
		while (pc < end) {
			int op = attr[pc] & 0xff;
			int len = OPLEN[op];
			if (len < 0) {
				throw new IOException(String.format("illegal opcode 0x%02x at offset %d", op, pc - start));
			}
			if (len == 0) {
				if (op == 0xc4) {
					if (pc + 1 >= end) {
						throw new IOException("truncated wide at offset " + (pc - start));
					}
					len = (attr[pc + 1] & 0xff) == 0x84 ? 6 : 4;
				} else {
					int p = start + (((pc - start) + 4) & ~3);
					long e;
					if (op == 0xaa) {
						if (p + 12 > end) {
							throw new IOException("truncated tableswitch at offset " + (pc - start));
						}
						long count = (long) s4(attr, p + 8) - s4(attr, p + 4) + 1;
						if (count < 0) {
							throw new IOException("bad tableswitch range at offset " + (pc - start));
						}
						e = p + 12L + count * 4;
					} else {
						if (p + 8 > end) {
							throw new IOException("truncated lookupswitch at offset " + (pc - start));
						}
						long pairs = s4(attr, p + 4);
						if (pairs < 0) {
							throw new IOException("bad lookupswitch at offset " + (pc - start));
						}
						e = p + 8L + pairs * 8;
					}
					if (e > end) {
						throw new IOException("switch runs past code end at offset " + (pc - start));
					}
					len = (int) (e - pc);
				}
			}
			if (pc + len > end) {
				throw new IOException("instruction runs past code end at offset " + (pc - start));
			}
			if (op >= 0xb2 && op <= 0xb9) {
				int idx = u2(attr, pc + 1);
				if (idx <= 0 || idx >= ci.tags.length) {
					throw new IOException("bad constant pool index " + idx + " at offset " + (pc - start));
				}
				int tag = ci.tags[idx];
				boolean field = op <= 0xb5;
				if (field ? tag != TAG_FIELDREF : (tag != TAG_METHODREF && tag != TAG_IMETHODREF)) {
					throw new IOException("constant pool tag " + tag + " does not fit opcode 0x" + Integer.toHexString(op)
							+ " at offset " + (pc - start));
				}
				boolean isStatic = op == 0xb2 || op == 0xb3 || op == 0xb8;
				(isStatic ? ci.st : ci.ns)[idx] = true;
			}
			pc += len;
		}
	}

	// ------------------------------------------------------------------------------------------------
	// resolution
	// ------------------------------------------------------------------------------------------------

	private enum Status { OK, MISSING, ERROR, UNVERIFIABLE }

	private static final class Lookup {
		ClassInfo declaring;
		int access;
		String boundary;
		String hint;

		boolean found() {
			return declaring != null;
		}
	}

	private static final class Checker {
		private final Map<String, ClassInfo> provider;
		private final Map<String, ClassInfo> jdk = new HashMap<>();
		private final Set<String> jdkAbsent = new HashSet<>();

		Checker(Map<String, ClassInfo> provider) {
			this.provider = provider;
		}

		/** Provider class, or a JDK class for java/ and javax/ names; null when the hierarchy leaves our knowledge. */
		ClassInfo load(String name) {
			ClassInfo ci = provider.get(name);
			if (ci != null) {
				return ci;
			}
			if (!name.startsWith("java/") && !name.startsWith("javax/")) {
				return null;
			}
			ci = jdk.get(name);
			if (ci != null) {
				return ci;
			}
			if (jdkAbsent.contains(name)) {
				return null;
			}
			try (InputStream in = ClassLoader.getSystemResourceAsStream(name + ".class")) {
				if (in != null) {
					ci = ClassInfo.parse(in.readAllBytes(), false);
					jdk.put(name, ci);
					return ci;
				}
			} catch (IOException | RuntimeException e) {
				// treated like an unknown class below
			}
			jdkAbsent.add(name);
			return null;
		}

		Lookup resolveField(String owner, String key) {
			Lookup lk = new Lookup();
			findField(owner, key, new HashSet<>(), lk);
			return lk;
		}

		private boolean findField(String cls, String key, Set<String> visited, Lookup lk) {
			ClassInfo c = load(cls);
			if (c == null) {
				if (lk.boundary == null) {
					lk.boundary = cls;
				}
				return false;
			}
			if (!visited.add(cls)) {
				return false;
			}
			Integer acc = c.fields.get(key);
			if (acc != null) {
				lk.declaring = c;
				lk.access = acc;
				return true;
			}
			for (String i : c.interfaces) {
				if (findField(i, key, visited, lk)) {
					return true;
				}
			}
			return c.superName != null && findField(c.superName, key, visited, lk);
		}

		Lookup resolveMethod(ClassInfo owner, String key) {
			Lookup lk = new Lookup();
			boolean isInterface = (owner.access & ACC_INTERFACE) != 0;
			Integer own = owner.methods.get(key);
			if (own != null) {
				lk.declaring = owner;
				lk.access = own;
				return lk;
			}
			if (key.startsWith("<")) {
				return lk; // initializers are not inherited
			}
			Set<String> visited = new HashSet<>();
			if (isInterface) {
				ClassInfo object = load("java/lang/Object");
				Integer acc = object == null ? null : object.methods.get(key);
				if (acc != null && (acc & ACC_PUBLIC) != 0 && (acc & ACC_STATIC) == 0) {
					lk.declaring = object;
					lk.access = acc;
					return lk;
				}
				visited.add(owner.name);
				for (String i : owner.interfaces) {
					if (findInterfaceMethod(i, key, visited, lk)) {
						return lk;
					}
				}
				return lk;
			}
			List<ClassInfo> chain = new ArrayList<>();
			chain.add(owner);
			Set<String> chainNames = new HashSet<>();
			chainNames.add(owner.name);
			String cur = owner.superName;
			while (cur != null && chainNames.add(cur)) {
				ClassInfo c = load(cur);
				if (c == null) {
					lk.boundary = cur;
					break;
				}
				chain.add(c);
				Integer acc = c.methods.get(key);
				if (acc != null) {
					lk.declaring = c;
					lk.access = acc;
					return lk;
				}
				cur = c.superName;
			}
			for (ClassInfo c : chain) {
				for (String i : c.interfaces) {
					if (findInterfaceMethod(i, key, visited, lk)) {
						return lk;
					}
				}
			}
			return lk;
		}

		private boolean findInterfaceMethod(String cls, String key, Set<String> visited, Lookup lk) {
			ClassInfo c = load(cls);
			if (c == null) {
				if (lk.boundary == null) {
					lk.boundary = cls;
				}
				return false;
			}
			if (!visited.add(cls)) {
				return false;
			}
			Integer acc = c.methods.get(key);
			if (acc != null && (acc & (ACC_STATIC | ACC_PRIVATE)) == 0) {
				lk.declaring = c;
				lk.access = acc;
				return true;
			}
			for (String i : c.interfaces) {
				if (findInterfaceMethod(i, key, visited, lk)) {
					return true;
				}
			}
			return false;
		}

		/** Descriptors of members with the same name somewhere in the (known) hierarchy, for the MISSING hint. */
		String sameNameHint(String owner, String name, boolean field) {
			Set<String> found = new LinkedHashSet<>();
			String needle = name + (field ? ":" : "(");
			Deque<String> todo = new ArrayDeque<>();
			Set<String> visited = new HashSet<>();
			todo.add(owner);
			while (!todo.isEmpty() && found.size() < 4) {
				String cls = todo.poll();
				ClassInfo c = visited.add(cls) ? load(cls) : null;
				if (c == null) {
					continue;
				}
				for (String k : (field ? c.fields : c.methods).keySet()) {
					if (k.startsWith(needle)) {
						found.add(k.substring(name.length() + (field ? 1 : 0)));
					}
				}
				if (c.superName != null) {
					todo.add(c.superName);
				}
				todo.addAll(Arrays.asList(c.interfaces));
			}
			if (found.isEmpty()) {
				return null;
			}
			return "a member with this name exists with descriptor " + String.join(", ", found);
		}
	}

	private static final class Item {
		final String text;
		final String usedBy;
		Status status = Status.OK;
		String reason;
		final List<String> warnings = new ArrayList<>();

		Item(String text, String usedBy) {
			this.text = text;
			this.usedBy = usedBy;
		}

		String line() {
			return text + "  (" + usedBy + ")" + (reason == null ? "" : "  -- " + reason);
		}
	}

	private static Item checkClass(ClassAgg c, Checker checker) {
		Item item = new Item(c.name.replace('/', '.'), c.usedBy());
		ClassInfo ci = checker.load(c.name);
		if (ci == null) {
			item.status = Status.MISSING;
			item.reason = "class not found in provider";
		} else if ((ci.access & ACC_PUBLIC) == 0 && c.otherPkgUse) {
			item.warnings.add(item.text + "  (" + item.usedBy + ")  -- class is package-private in the provider");
		}
		return item;
	}

	private static Item checkMember(RefAgg r, Checker checker) {
		Item item = new Item(r.display(), r.usedBy());
		boolean field = r.isField();
		ClassInfo owner = checker.load(r.owner);
		if (owner == null) {
			item.status = Status.MISSING;
			item.reason = "owner class not found in provider";
			return item;
		}
		boolean ownerIsInterface = (owner.access & ACC_INTERFACE) != 0;
		if (!field) {
			if (r.tag == TAG_METHODREF && ownerIsInterface) {
				item.status = Status.ERROR;
				item.reason = "Methodref on an interface, owner is an interface in the provider (IncompatibleClassChangeError)";
				return item;
			}
			if (r.tag == TAG_IMETHODREF && !ownerIsInterface) {
				item.status = Status.ERROR;
				item.reason = "InterfaceMethodref on a class, owner is a class in the provider (IncompatibleClassChangeError)";
				return item;
			}
		}
		Lookup lk = field ? checker.resolveField(r.owner, r.name + ":" + r.desc) : checker.resolveMethod(owner, r.name + r.desc);
		if (!lk.found()) {
			if (lk.boundary != null) {
				item.status = Status.UNVERIFIABLE;
				item.reason = "hierarchy leaves the provider at " + lk.boundary.replace('/', '.');
			} else {
				item.status = Status.MISSING;
				String hint = checker.sameNameHint(r.owner, r.name, field);
				item.reason = (field ? "field" : "method") + " not found" + (hint == null ? "" : "; " + hint);
			}
			return item;
		}
		List<String> errors = new ArrayList<>();
		boolean isStatic = (lk.access & ACC_STATIC) != 0;
		if (isStatic && r.usedNonStatic) {
			errors.add((field ? "getfield/putfield" : "invokevirtual/special/interface") + " on a static "
					+ (field ? "field" : "method") + " (IncompatibleClassChangeError)");
		}
		if (!isStatic && r.usedStatic) {
			errors.add((field ? "getstatic/putstatic" : "invokestatic") + " on a non-static "
					+ (field ? "field" : "method") + " (IncompatibleClassChangeError)");
		}
		if ((lk.access & ACC_PRIVATE) != 0 && r.nonNestUse) {
			errors.add("private in " + lk.declaring.name.replace('/', '.') + " (IllegalAccessError)");
		}
		if (!errors.isEmpty()) {
			item.status = Status.ERROR;
			item.reason = String.join("; ", errors);
			return item;
		}
		if ((lk.access & (ACC_PUBLIC | ACC_PRIVATE)) == 0 && r.otherPkgUse) {
			String kind = (lk.access & ACC_PROTECTED) != 0 ? "protected" : "package-private";
			item.warnings.add(item.text + "  (" + item.usedBy + ")  -- " + kind + " in "
					+ lk.declaring.name.replace('/', '.') + " (fails with IllegalAccessError unless the consumer is a subclass or in the same package)");
		}
		return item;
	}

	// ------------------------------------------------------------------------------------------------
	// report
	// ------------------------------------------------------------------------------------------------

	/** Appends the section for one consumer jar; returns the number of MISSING plus ERROR references. */
	private static int report(StringBuilder rep, Path consumerJar, ConsumerScan scan, Checker checker, Options opt) {
		List<Item> items = new ArrayList<>();
		int classRefs = 0;
		int methodRefs = 0;
		int fieldRefs = 0;
		int excluded = 0;
		for (ClassAgg c : scan.classes.values()) {
			if (!opt.includeSelf && scan.ownClasses.contains(c.name)) {
				excluded++;
				continue;
			}
			classRefs++;
			items.add(checkClass(c, checker));
		}
		for (RefAgg r : scan.refs.values()) {
			if (!opt.includeSelf && scan.ownClasses.contains(r.owner)) {
				continue;
			}
			if (r.isField()) {
				fieldRefs++;
			} else {
				methodRefs++;
			}
			items.add(checkMember(r, checker));
		}

		int[] counts = new int[Status.values().length];
		List<String> missing = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		List<String> unverifiable = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		List<String> all = new ArrayList<>();
		for (Item it : items) {
			counts[it.status.ordinal()]++;
			switch (it.status) {
				case MISSING -> missing.add(it.line());
				case ERROR -> errors.add(it.line());
				case UNVERIFIABLE -> unverifiable.add(it.line());
				default -> { }
			}
			warnings.addAll(it.warnings);
			all.add(String.format("%-12s %s", it.status, it.line()));
		}
		for (String w : scan.warnings) {
			warnings.add(w);
		}
		Collections.sort(missing);
		Collections.sort(errors);
		Collections.sort(unverifiable);
		Collections.sort(warnings);
		Collections.sort(all);

		rep.append(NL).append("=== consumer: ").append(consumerJar.getFileName()).append(" ===").append(NL);
		rep.append("path: ").append(consumerJar).append(NL);
		rep.append("classes scanned: ").append(scan.classesScanned).append(NL);
		rep.append("distinct references with prefix: classes=").append(classRefs).append(" methods=").append(methodRefs)
				.append(" fields=").append(fieldRefs).append(NL);
		if (excluded > 0) {
			rep.append("classes defined by the consumer itself and skipped: ").append(excluded).append(NL);
		}
		rep.append("OK=").append(counts[Status.OK.ordinal()])
				.append(" MISSING=").append(counts[Status.MISSING.ordinal()])
				.append(" ERROR=").append(counts[Status.ERROR.ordinal()])
				.append(" UNVERIFIABLE=").append(counts[Status.UNVERIFIABLE.ordinal()])
				.append(" warnings=").append(warnings.size());
		if (scan.walkFailures > 0) {
			rep.append(" (bytecode walk failures: ").append(scan.walkFailures).append(")");
		}
		rep.append(NL);
		if (classRefs + methodRefs + fieldRefs == 0) {
			rep.append("note: 0 references with prefix ").append(opt.prefix).append(" in this consumer").append(NL);
		}
		appendSection(rep, "MISSING", missing);
		appendSection(rep, "ERRORS", errors);
		appendSection(rep, "UNVERIFIABLE", unverifiable);
		appendSection(rep, "WARNINGS", warnings);
		if (opt.verbose) {
			appendSection(rep, "ALL CHECKED REFERENCES", all);
		}
		return counts[Status.MISSING.ordinal()] + counts[Status.ERROR.ordinal()];
	}

	private static void appendSection(StringBuilder rep, String title, List<String> lines) {
		rep.append(NL).append(title).append(" (").append(lines.size()).append("):").append(NL);
		for (String l : lines) {
			rep.append("  ").append(l).append(NL);
		}
	}
}
