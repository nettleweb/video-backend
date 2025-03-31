#include <jni.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <iostream>
#include <sys/stat.h>
#include <sys/types.h>

using namespace std;

extern "C" {
	void Java_com_nettleweb_client_VM_init(JNIEnv *env, jclass cls) {
		sync();
		nice(10);
		setenv("LANG", "C", 1);
		setenv("LC_ALL", "C", 1);
		setresuid(0, 0, 0);
		setresgid(0, 0, 0);
		setlocale(LC_ALL, "C.UTF-8");
	}

	void Java_com_nettleweb_client_VM_sync(JNIEnv *env, jclass cls) {
		sync();
	}

	void Java_com_nettleweb_client_VM_exit(JNIEnv *env, jclass cls, jint code) {
		exit((int) code);
	}

	void Java_com_nettleweb_client_VM_free(JNIEnv *env, jclass cls, jlong ptr) {
		free((void *) ptr);
	}

	jlong Java_com_nettleweb_client_VM_malloc(JNIEnv *env, jclass cls, jlong size) {
		return (jlong) malloc((size_t) size);
	}

	void Java_com_nettleweb_client_VM_buffer(JNIEnv *env, jclass cls, jlong ptr, jbyteArray buf, jint off, jint len) {
		env->SetByteArrayRegion(buf, off, len, (const jbyte*) ptr);
	}

	jint Java_com_nettleweb_client_VM_kill(JNIEnv *env, jclass cls, jint pid, jint sig) {
		return (jint) kill((pid_t) pid, (int) sig);
	}

	jint Java_com_nettleweb_client_VM_open(JNIEnv *env, jclass cls, jstring file, jint flags) {
		const char* data = env->GetStringUTFChars(file, 0);
		const jint fd = (jint) open(data, flags);
		env->ReleaseStringUTFChars(file, data);
		return fd;
	}

	jint Java_com_nettleweb_client_VM_read(JNIEnv *env, jclass cls, jint fd, jbyteArray buf, jint off, jint len) {
		void* buffer = malloc((size_t) len);
		const jint size = (jint) read((int) fd, buffer, (size_t) len);
		env->SetByteArrayRegion(buf, off, len, (const jbyte*) buffer);
		free(buffer);
		return size;
	}

	jint Java_com_nettleweb_client_VM_write(JNIEnv *env, jclass cls, jint fd, jbyteArray buf, jint off, jint len) {
		void* buffer = malloc((size_t) len);
		env->GetByteArrayRegion(buf, off, len, (jbyte*) buffer);
		const jint size = (jint) write((int) fd, buffer, (size_t) len);
		free(buffer);
		return size;
	}

	jint Java_com_nettleweb_client_VM_fsync(JNIEnv *env, jclass cls, jint fd) {
		return (jint) fsync((int) fd);
	}

	jint Java_com_nettleweb_client_VM_close(JNIEnv *env, jclass cls, jint fd) {
		return (jint) close((int) fd);
	}

	jint Java_com_nettleweb_client_VM_mkdir(JNIEnv *env, jclass cls, jstring path, jint mode) {
		const char* data = env->GetStringUTFChars(path, 0);
		const jint code = (jint) mkdir(data, (mode_t) mode);
		env->ReleaseStringUTFChars(path, data);
		return code;
	}

	jint Java_com_nettleweb_client_VM_rmdir(JNIEnv *env, jclass cls, jstring path) {
		const char* data = env->GetStringUTFChars(path, 0);
		const jint code = (jint) rmdir(data);
		env->ReleaseStringUTFChars(path, data);
		return code;
	}

	jint Java_com_nettleweb_client_VM_chmod(JNIEnv *env, jclass cls, jstring path, jint mode) {
		const char* data = env->GetStringUTFChars(path, 0);
		const jint code = (jint) chmod(data, (mode_t) mode);
		env->ReleaseStringUTFChars(path, data);
		return code;
	}

	jint Java_com_nettleweb_client_VM_chown(JNIEnv *env, jclass cls, jstring path, jint uid, jint gid) {
		const char* data = env->GetStringUTFChars(path, 0);
		const jint code = (jint) chown(data, (uid_t) uid, (gid_t) gid);
		env->ReleaseStringUTFChars(path, data);
		return code;
	}

	jint Java_com_nettleweb_client_VM_sleep(JNIEnv *env, jclass cls, jint sec) {
		return (jint) sleep((unsigned int) sec);
	}

	jint Java_com_nettleweb_client_VM_system(JNIEnv *env, jclass cls, jstring cmd) {
		const char* data = env->GetStringUTFChars(cmd, 0);
		const jint code = (jint) system(data);
		env->ReleaseStringUTFChars(cmd, data);
		return code;
	}
}

int main(const int argc, const char** argv) {
	return 0;
}
