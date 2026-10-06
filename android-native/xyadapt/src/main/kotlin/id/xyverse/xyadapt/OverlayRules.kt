package id.xyverse.xyadapt

/**
 * Kapan kontrol di layar ditampilkan, dan yang mana.
 *
 * ## Masalah yang diperbaiki aturan ini
 *
 * Sebelumnya keputusannya satu saklar untuk semuanya: begitu ada **satu**
 * perangkat fisik terdeteksi — keyboard Bluetooth, mouse OTG, apa saja —
 * seluruh lapisan kontrol disembunyikan. Akibatnya menempelkan keyboard
 * Bluetooth ikut menghapus gamepad virtual dari layar, padahal tidak ada
 * gamepad fisik di mana pun, dan pengguna tidak punya cara mengembalikannya.
 *
 * Aturan sekarang bekerja **per keluarga perangkat**: tombol yang sudah ada
 * padanan fisiknya saja yang menyingkir. Keyboard fisik menyembunyikan
 * tombol huruf; stik dan tombol gamepad tetap di tempatnya.
 *
 * Semuanya murni fungsi supaya bisa diuji tanpa perangkat, tanpa Android,
 * dan tanpa sesi.
 */
enum class InputFamily {
    /** Tombol huruf, kombinasi, dan stik yang mengirim tombol keyboard. */
    KEYBOARD,

    /** Klik, scroll, dan stik yang menggerakkan kursor. */
    MOUSE,

    /** Tombol, stik, dan trigger XInput. */
    GAMEPAD,

    /** Kontrol yang tidak punya padanan fisik (mis. ganti mode sentuh). */
    SHARED,
}

object OverlayRules {
    /** Tampilkan sesuai perangkat yang terdeteksi — bawaan. */
    const val MODE_AUTO = 0

    /** Selalu tampilkan, walau ada keyboard/mouse/gamepad fisik. */
    const val MODE_ALWAYS = 1

    /** Jangan pernah tampilkan. */
    const val MODE_OFF = 2

    /** Mode yang dikenal; nilai tersimpan di luar rentang ini jatuh ke AUTO. */
    fun normalizeMode(mode: Int): Int = if (mode in MODE_AUTO..MODE_OFF) mode else MODE_AUTO

    /**
     * Keluarga sebuah kontrol dari nama `OverlayKind`-nya.
     *
     * Nama dioper sebagai string, bukan enum, supaya modul murni ini tidak
     * perlu bergantung pada modul aplikasi. Nama yang tidak dikenal masuk
     * [InputFamily.SHARED] — kontrol baru lebih baik ikut terlihat daripada
     * hilang diam-diam karena aturan ini belum diperbarui.
     */
    fun family(kind: String): InputFamily = when (kind.uppercase()) {
        "KEY", "CHORD", "STICK_KEYS" -> InputFamily.KEYBOARD
        "MOUSE", "SCROLL", "SCROLL_X", "SCROLL_WHEEL", "STICK_MOUSE" -> InputFamily.MOUSE
        "GAMEPAD_BUTTON", "GAMEPAD_STICK_L", "GAMEPAD_STICK_R",
        "GAMEPAD_TRIGGER_L", "GAMEPAD_TRIGGER_R",
        -> InputFamily.GAMEPAD
        else -> InputFamily.SHARED
    }

    /**
     * Apakah satu kontrol ditampilkan.
     *
     * [edit] = pengguna sedang mengatur tata letak. Saat mengatur, semuanya
     * terlihat: tombol yang disembunyikan otomatis tetap harus bisa dipindah
     * dan dihapus, kalau tidak ia menjadi mustahil diurus justru pada saat
     * pengguna sedang mengurusnya.
     */
    fun visible(
        kind: String,
        mode: Int,
        keyboard: Boolean = false,
        mouse: Boolean = false,
        gamepad: Boolean = false,
        edit: Boolean = false,
    ): Boolean {
        val m = normalizeMode(mode)
        if (m == MODE_OFF) return false
        if (edit || m == MODE_ALWAYS) return true
        return when (family(kind)) {
            InputFamily.KEYBOARD -> !keyboard
            InputFamily.MOUSE -> !mouse
            InputFamily.GAMEPAD -> !gamepad
            InputFamily.SHARED -> true
        }
    }

    /**
     * Apakah lapisan kontrol perlu digambar sama sekali. Dipakai untuk
     * mematikan seluruh View-nya — lapisan kosong yang tetap hidup masih
     * ikut dalam setiap lintasan tata letak dan sentuhan.
     */
    fun layerVisible(
        kinds: List<String>,
        mode: Int,
        keyboard: Boolean = false,
        mouse: Boolean = false,
        gamepad: Boolean = false,
        edit: Boolean = false,
    ): Boolean {
        if (edit && normalizeMode(mode) != MODE_OFF) return true
        return kinds.any { visible(it, mode, keyboard, mouse, gamepad, edit) }
    }

    /** Nama mode untuk tombol di panel. */
    fun modeLabel(mode: Int): String = when (normalizeMode(mode)) {
        MODE_ALWAYS -> "Kontrol selalu tampil"
        MODE_OFF -> "Kontrol disembunyikan"
        else -> "Kontrol otomatis"
    }

    /** Klik berikutnya pada tombol mode. */
    fun nextMode(mode: Int): Int = when (normalizeMode(mode)) {
        MODE_AUTO -> MODE_ALWAYS
        MODE_ALWAYS -> MODE_OFF
        else -> MODE_AUTO
    }

    /**
     * Kalimat yang menjelaskan apa yang baru saja terdeteksi dan akibatnya.
     * Kosong berarti tidak ada yang perlu dikatakan — memberi tahu pengguna
     * bahwa "tidak ada perangkat terdeteksi" hanya menambah kebisingan.
     */
    fun detectionMessage(
        mode: Int,
        keyboard: Boolean,
        mouse: Boolean,
        gamepad: Boolean,
    ): String {
        val ada = buildList {
            if (keyboard) add("Keyboard")
            if (mouse) add("Mouse")
            if (gamepad) add("Gamepad")
        }
        if (ada.isEmpty()) return ""
        val daftar = when (ada.size) {
            1 -> ada[0]
            2 -> "${ada[0]} dan ${ada[1]}"
            else -> "${ada.dropLast(1).joinToString(", ")}, dan ${ada.last()}"
        }
        return when (normalizeMode(mode)) {
            MODE_ALWAYS -> "$daftar terdeteksi. Kontrol di layar tetap tampil."
            MODE_OFF -> "$daftar terdeteksi."
            else -> "$daftar terdeteksi. Tombol di layar yang digantikannya disembunyikan."
        }
    }
}
