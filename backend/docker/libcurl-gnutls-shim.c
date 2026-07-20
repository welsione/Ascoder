/* libcurl-gnutls-shim.c
 *
 * Shim library that re-exports libcurl-openssl symbols under the CURL_GNUTLS_3
 * version tag, allowing git-remote-https (linked against libcurl-gnutls)
 * to use OpenSSL for TLS instead of GnuTLS.
 *
 * Build:
 *   gcc -shared -fPIC -o libcurl-gnutls-shim.so libcurl-gnutls-shim.c \
 *       -L/usr/lib/aarch64-linux-gnu -lcurl -Wl,--version-script=libcurl-gnutls-shim.map
 */
