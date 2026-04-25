@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.natskt.tls.cert

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.mkdtemp
import platform.posix.system

internal class CaResult(
	val keyPath: String,
	val pemPath: String,
	val der: ByteArray,
)

internal class TestCertGenerator {
	val dir: String =
		run {
			val template = "/tmp/natskt-test-certs-XXXXXX".encodeToByteArray().copyOf(64)
			template.usePinned { pinned ->
				mkdtemp(pinned.addressOf(0).reinterpret())?.toKString()
					?: error("mkdtemp failed")
			}
		}

	private var counter = 0

	private fun nextPrefix(): String = "c${counter++}"

	fun generateCa(cn: String): CaResult {
		val p = nextPrefix()
		val key = "$dir/$p-ca.key"
		val pem = "$dir/$p-ca.pem"

		sh(
			"openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1" +
				" -keyout $key -out $pem -days 1 -nodes -subj '/CN=$cn'" +
				" -addext 'basicConstraints=critical,CA:TRUE'" +
				" -addext 'keyUsage=critical,keyCertSign'",
		)

		return CaResult(key, pem, pemToDer(pem, "$dir/$p-ca.der"))
	}

	fun generateLeaf(
		ca: CaResult,
		cn: String,
		sans: String,
	): ByteArray {
		val p = nextPrefix()
		val key = "$dir/$p.key"
		val csr = "$dir/$p.csr"
		val pem = "$dir/$p.pem"

		sh(
			"openssl req -newkey ec -pkeyopt ec_paramgen_curve:prime256v1" +
				" -keyout $key -out $csr -nodes -subj '/CN=$cn'" +
				" -addext 'subjectAltName=$sans'",
		)
		sh(
			"openssl x509 -req -in $csr -CA ${ca.pemPath} -CAkey ${ca.keyPath}" +
				" -CAcreateserial -out $pem -days 1 -copy_extensions copyall",
		)

		return pemToDer(pem, "$dir/$p.der")
	}

	fun generateExpiredLeaf(
		ca: CaResult,
		cn: String,
		sans: String,
	): ByteArray {
		val p = nextPrefix()
		val key = "$dir/$p.key"
		val csr = "$dir/$p.csr"
		val pem = "$dir/$p.pem"

		sh(
			"openssl req -newkey ec -pkeyopt ec_paramgen_curve:prime256v1" +
				" -keyout $key -out $csr -nodes -subj '/CN=$cn'" +
				" -addext 'subjectAltName=$sans'",
		)
		sh(
			"openssl x509 -req -in $csr -CA ${ca.pemPath} -CAkey ${ca.keyPath}" +
				" -CAcreateserial -out $pem -days 0 -copy_extensions copyall",
		)

		return pemToDer(pem, "$dir/$p.der")
	}

	fun generateIntermediateCa(
		rootCa: CaResult,
		cn: String,
	): CaResult {
		val p = nextPrefix()
		val key = "$dir/$p-int.key"
		val csr = "$dir/$p-int.csr"
		val pem = "$dir/$p-int.pem"
		val ext = "$dir/$p-int.ext"

		sh(
			"openssl req -newkey ec -pkeyopt ec_paramgen_curve:prime256v1" +
				" -keyout $key -out $csr -nodes -subj '/CN=$cn'",
		)
		writeText(ext, "basicConstraints=critical,CA:TRUE\nkeyUsage=critical,keyCertSign\n")
		sh(
			"openssl x509 -req -in $csr -CA ${rootCa.pemPath} -CAkey ${rootCa.keyPath}" +
				" -CAcreateserial -out $pem -days 1 -extfile $ext",
		)

		return CaResult(key, pem, pemToDer(pem, "$dir/$p-int.der"))
	}

	private fun pemToDer(
		pemPath: String,
		derPath: String,
	): ByteArray {
		sh("openssl x509 -in $pemPath -outform DER -out $derPath")
		return readBytes(derPath)
	}

	private fun sh(command: String) {
		val exitCode = system(command)
		if (exitCode != 0) error("Command failed (exit $exitCode): $command")
	}
}

private fun readBytes(path: String): ByteArray {
	val f = fopen(path, "rb") ?: error("Cannot open: $path")
	try {
		fseek(f, 0, SEEK_END)
		val size = ftell(f).toInt()
		fseek(f, 0, SEEK_SET)
		val buf = ByteArray(size)
		buf.usePinned { pinned ->
			fread(pinned.addressOf(0), 1u, size.toULong(), f)
		}
		return buf
	} finally {
		fclose(f)
	}
}

private fun writeText(
	path: String,
	content: String,
) {
	val f = fopen(path, "w") ?: error("Cannot write: $path")
	try {
		val bytes = content.encodeToByteArray()
		bytes.usePinned { pinned ->
			fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), f)
		}
	} finally {
		fclose(f)
	}
}
