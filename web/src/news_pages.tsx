import { NEWS_IMAGE_BLOCK, Route } from './app_routes';
import { useEffect, useRef, useState } from 'react';
import type { ReactElement } from 'react';
import {
  ApiError,
  me,
  } from './api';
import {
  explainError,
  NewsGridSkeleton,
  StateNotice,
  useOnline,
  useReload,
} from './states';
import {
  beginGoogleLogin,
  getStoredGoogleIdToken,
  clearStoredGoogleIdToken,
  } from './google';
import {
  ADMIN_EMAIL,
  fetchNewsList,
  fetchNewsPost,
  formatNewsDate,
  formatRelativeTime,
  getAdminToken,
  NEWS_CATEGORIES,
  NEWS_SHARE_BASE,
  NewsComment,
  NewsPost,
  newsAvatarUrl,
  postComment,
  setAdminToken,
  subscribeNews,
  toggleLike,
} from './news';

import {
  CHANGELOG_SLUG,
  } from './version';
import { WhatsAppIcon, TelegramIcon, XIcon, FacebookIcon } from './brand-icons';

export function AuthorName({
  name,
  official,
  size = 'sm',
  avatar = true,
}: {
  name: string;
  official?: boolean;
  size?: 'sm' | 'md';
  /// Foto founder menempel di byline artikel, tapi JANGAN di komentar —
  /// kepala komentar sudah punya avatar sendiri; kalau keduanya jalan,
  /// komentar resmi tampil dengan foto yang sama dua kali.
  avatar?: boolean;
}) {
  if (!official) return <span className="author-name">{name}</span>;
  return (
    <span className={`author-name official ${size}`}>
      {avatar && <img className="author-badge" src="/team/founder.jpg" alt="" aria-hidden="true" />}
      <strong>{name}</strong>
      <span className="official-tag" title="Akun resmi XySpace — Haekal Saputra">
        XySpace
      </span>
    </span>
  );
}

export function NewsCard({
  post,
  onOpen,
}: {
  post: NewsPost;
  onOpen: () => void;
}) {
  return (
    <article className="news-card" onClick={onOpen}>
      <div className="news-card-cover">
        <img src={post.cover} alt="" loading="lazy" />
        <span className="news-cat">{post.category}</span>
      </div>
      <div className="news-card-body">
        <h3>{post.title}</h3>
        <p>{post.excerpt}</p>
        <div className="news-card-meta">
          <span>{formatNewsDate(post.createdAt)}</span>
          <span>
            ♥ {post.likeCount} · 💬 {post.commentCount}
          </span>
        </div>
      </div>
    </article>
  );
}

export function NewsPage({ navigate }: { navigate: (r: Route) => void }) {
  const [category, setCategory] = useState<string>('semua');
  const [posts, setPosts] = useState<NewsPost[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  // `nonce` sengaja: tombol ulang yang memanggil setCategory(nilaiYangSama)
  // tidak pernah menjalankan ulang efeknya, jadi tombolnya percuma.
  const [nonce, reload] = useReload();
  const online = useOnline();

  useEffect(() => {
    let alive = true;
    setPosts(null);
    setError(null);
    fetchNewsList(category)
      .then((r) => {
        if (alive) setPosts(r.posts);
      })
      .catch((e) => {
        if (alive) setError(e);
      });
    return () => {
      alive = false;
    };
  }, [category, nonce]);

  return (
    <main className="content-page news-page">
      <p className="eyebrow">XYDESK NEWS</p>
      <h1>Berita & catatan rilis.</h1>
      <p className="page-lead">
        Pembaruan produk, keputusan teknik, dan angka yang kami ukur sendiri —
        tanpa jargon kosong.
      </p>

      <div className="news-cats">
        {NEWS_CATEGORIES.map((c) => (
          <button
            key={c}
            className={category === c ? 'active' : ''}
            onClick={() => setCategory(c)}
          >
            {c === 'semua' ? 'Semua' : c}
          </button>
        ))}
      </div>

      {error !== null && (
        <StateNotice
          tone={online ? 'error' : 'offline'}
          glyph={online ? 'alert' : 'cloud'}
          title={online ? 'Beritanya belum bisa dimuat' : 'Kamu sedang offline'}
          message={explainError(error, 'Gagal memuat berita.', online)}
          actionLabel="Coba lagi"
          onAction={reload}
        />
      )}

      {!posts && !error && <NewsGridSkeleton />}

      {posts && posts.length === 0 && (
        <StateNotice
          tone="empty"
          glyph="news"
          title={
            category === 'semua'
              ? 'Belum ada berita'
              : `Belum ada berita di kategori ${category}`
          }
          message="Begitu ada kabar baru, ia muncul di sini lebih dulu."
          {...(category === 'semua'
            ? {}
            : { actionLabel: 'Lihat semua berita', onAction: () => setCategory('semua') })}
        />
      )}

      {posts && posts.length > 0 && (
        <div className="news-grid">
          {posts.map((p) => (
            <NewsCard
              key={p.slug}
              post={p}
              onOpen={() => navigate({ page: 'news-detail', slug: p.slug })}
            />
          ))}
        </div>
      )}
    </main>
  );
}

export function NewsDetailPage({
  slug,
  navigate,
}: {
  slug: string;
  navigate: (r: Route) => void;
}) {
  const [data, setData] = useState<{ post: NewsPost; comments: NewsComment[] } | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [nonce, reload] = useReload();
  const online = useOnline();
  const [likeCount, setLikeCount] = useState(0);
  const [liked, setLiked] = useState(false);
  const [commentText, setCommentText] = useState('');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [replyTo, setReplyTo] = useState<NewsComment | null>(null);
  // Mode founder: login Google xycdigital@gmail.com membuka UI-nya;
  // keabsahan badge tetap diputuskan worker dari ADMIN_TOKEN.
  const [adminEligible, setAdminEligible] = useState(false);
  const [adminToken, setAdminTokenState] = useState(getAdminToken);
  const [googleToken, setGoogleToken] = useState<string | null>(getStoredGoogleIdToken);
  // Tombol lompat: panah bawah menuju komentar di dasar artikel; setelah
  // sampai, berubah jadi panah atas untuk kembali ke judul. Artikel
  // changelog bisa panjang — tanpa ini pembaca HP harus menggulir jauh.
  const [diDasar, setDiDasar] = useState(false);
  useEffect(() => {
    const cek = () => {
      const d = document.documentElement;
      setDiDasar(window.innerHeight + window.scrollY >= d.scrollHeight - 90);
    };
    cek();
    window.addEventListener('scroll', cek, { passive: true });
    return () => window.removeEventListener('scroll', cek);
  }, []);
  const [adminDraft, setAdminDraft] = useState('');
  useEffect(() => {
    const jwt = localStorage.getItem('xydesk.web.jwt');
    if (!jwt) return;
    me(jwt)
      .then((r) => setAdminEligible(r.user?.email?.toLowerCase() === ADMIN_EMAIL))
      .catch(() => {});
  }, []);
  // Admin aktif bila founder dan (Google id_token masih berlaku ATAU sudah
  // menempel ADMIN_TOKEN). Google id_token diutamakan saat kirim komentar.
  const adminActive = adminEligible && (googleToken !== null || adminToken !== '');
  // Form komentar ada di BAWAH daftar; saat "Balas" ditekan dari komentar
  // paling atas, gulirkan ke form supaya pengguna tidak mencarinya.
  const commentFormRef = useRef<HTMLDivElement | null>(null);
  const focusCommentForm = (target: NewsComment | null) => {
    setReplyTo(target);
    setNotice('');
    requestAnimationFrame(() => {
      commentFormRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      commentFormRef.current?.querySelector('textarea')?.focus({ preventScroll: true });
    });
  };
  const [email, setEmail] = useState('');
  const [subBusy, setSubBusy] = useState(false);

  useEffect(() => {
    let alive = true;
    setData(null);
    setError(null);
    fetchNewsPost(slug)
      .then((r) => {
        if (!alive) return;
        setData(r);
        setLikeCount(r.post.likeCount);
        setLiked(r.liked || localStorage.getItem(`xydesk.news.liked.${slug}`) === '1');
      })
      .catch((e) => {
        if (alive) setError(e);
      });
    return () => {
      alive = false;
    };
  }, [slug, nonce]);

  const post = data?.post;

  // Tautan versi di footer menuju `CHANGELOG_SLUG` (mis. changelog-v6-4-0).
  // Kalau rilis lupa menerbitkan artikel dengan slug itu, artikelnya jatuh ke
  // hash acak `p-…` dan halaman ini kena 404. Alih-alih menampilkan pesan
  // galat mentah, tampilkan penjelasan + jalan keluar ke daftar berita.
  const isChangelogMissing =
    slug === CHANGELOG_SLUG && online && error instanceof ApiError && error.status === 404;

  // Like OPTIMISTIK: UI berubah seketika, server menyusul — kalau gagal,
  // kembalikan ke keadaan sebelumnya. Ini membuat like terasa instan.
  const like = () => {
    if (!post || busy) return;
    const target = !liked;
    setLiked(target);
    setLikeCount((n) => n + (target ? 1 : -1));
    toggleLike(post.slug)
      .then((r) => {
        setLiked(r.liked);
        setLikeCount(r.likeCount);
        localStorage.setItem(`xydesk.news.liked.${post.slug}`, r.liked ? '1' : '0');
      })
      .catch((e) => {
        setLiked(!target);
        setLikeCount((n) => n + (target ? -1 : 1));
        setNotice(e instanceof Error ? e.message : 'Gagal memproses like.');
      });
  };

  const submitComment = async () => {
    if (!post || commentText.trim().length < 2 || busy) return;
    setBusy(true);
    setNotice('');
    try {
      // Username acak per perangkat — kecuali mode founder yang terverifikasi
      // server: tampil sebagai Haekal Saputra + badge XySpace.
      // Cek ulang id_token saat kirim (bisa saja kedaluwarsa sejak halaman
      // dibuka); bila masih ada, pakai jalur Google — bila tidak, fallback
      // ke ADMIN_TOKEN yang ditempel. Kalau keduanya kosong, komentar jatuh
      // ke mode publik biasa (nama acak), bukan kredensial kosong.
      const gt = getStoredGoogleIdToken();
      if (gt !== googleToken) setGoogleToken(gt);
      const adminCredential = gt
        ? { googleToken: gt }
        : adminToken
          ? { token: adminToken }
          : undefined;
      const r = await postComment(
        post.slug,
        commentText.trim(),
        replyTo?.id ?? null,
        adminCredential,
      );
      setData((d) =>
        d
          ? { post: { ...d.post, commentCount: d.post.commentCount + 1 }, comments: [...d.comments, r.comment] }
          : d,
      );
      setCommentText('');
      setReplyTo(null);
      setNotice('Komentar terkirim.');
    } catch (e) {
      setNotice(e instanceof Error ? e.message : 'Gagal mengirim komentar.');
    } finally {
      setBusy(false);
    }
  };

  const subscribe = async () => {
    if (!email.includes('@') || subBusy) return;    setSubBusy(true);
    setNotice('');
    try {
      await subscribeNews(email.trim());
      setEmail('');
      setNotice('Berhasil! Email kamu terdaftar untuk berita XyDesk.');
    } catch (e) {
      setNotice(e instanceof Error ? e.message : 'Gagal mendaftar email.');
    } finally {
      setSubBusy(false);
    }
  };

  const shareUrl = post ? `${NEWS_SHARE_BASE}/${post.slug}` : '';

  const share = async () => {
    if (!post) return;
    const dataToShare = { title: post.title, text: post.excerpt, url: shareUrl };
    if (navigator.share) {
      try {
        await navigator.share(dataToShare);
        return;
      } catch {
        /* batal / tidak didukung → tombol manual tetap tersedia */
      }
    }
    try {
      await navigator.clipboard.writeText(shareUrl);
      setNotice('Tautan disalin — tempel ke media sosial.');
    } catch {
      setNotice(shareUrl);
    }
  };

  const socials: { label: string; href: string; Icon: (p: { size?: number }) => ReactElement }[] =
    post
      ? [
          {
            label: 'WhatsApp',
            Icon: WhatsAppIcon,
            href: `https://wa.me/?text=${encodeURIComponent(`${post.title} ${shareUrl}`)}`,
          },
          {
            label: 'Telegram',
            Icon: TelegramIcon,
            href: `https://t.me/share/url?url=${encodeURIComponent(shareUrl)}&text=${encodeURIComponent(post.title)}`,
          },
          {
            label: 'X',
            Icon: XIcon,
            href: `https://twitter.com/intent/tweet?text=${encodeURIComponent(post.title)}&url=${encodeURIComponent(shareUrl)}`,
          },
          {
            label: 'Facebook',
            Icon: FacebookIcon,
            href: `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(shareUrl)}`,
          },
        ]
      : [];

  const comments = data?.comments ?? [];
  const topLevel = comments.filter((c) => c.parentId == null);

  return (
    <main className="content-page news-detail">
      <button className="back-action" onClick={() => navigate('/news')}>
        ← Semua berita
      </button>

      {error !== null && (
        isChangelogMissing ? (
          <StateNotice
            tone="empty"
            glyph="news"
            title="Catatan rilis versi ini belum tersedia"
            message="Artikel changelog untuk versi ini belum diterbitkan di XyDesk News. Kamu tetap bisa membaca berita terbaru dari daftar semua berita."
            actionLabel="Semua berita"
            onAction={() => navigate('/news')}
          />
        ) : (
          <StateNotice
            tone={online ? 'error' : 'offline'}
            glyph={online ? 'alert' : 'cloud'}
            title={online ? 'Berita ini belum bisa dibuka' : 'Kamu sedang offline'}
            message={explainError(error, 'Berita tidak ditemukan.', online)}
            actionLabel="Coba lagi"
            onAction={reload}
          />
        )
      )}

      {!post && !error && (
        <div className="news-detail-skeleton">
          <div className="sk sk-block cover" />
          <div className="sk-line w80 big" />
          <div className="sk-line w100" />
          <div className="sk-line w100" />
          <div className="sk-line w40" />
        </div>
      )}

      {post && (
        <article className="post">
          <div className="post-cover">
            <img src={post.cover} alt="" />
          </div>
          <span className="news-cat">{post.category}</span>
          <h1>{post.title}</h1>
          <div className="post-meta">
            {/* Artikel hanya bisa diterbitkan lewat endpoint admin, jadi
                penulisnya resmi menurut konstruksi. */}
            <AuthorName name={post.author} official size="md" />
            <span>·</span>
            <span>{formatNewsDate(post.createdAt)}</span>
          </div>

          <div className="post-actions">
            <button
              className={`like-btn ${liked ? 'liked' : ''}`}
              onClick={like}
              title={liked ? 'Batal suka' : 'Suka'}
            >
              <svg viewBox="0 0 24 24" width="16" height="16" fill={liked ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="2">
                <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7z" />
              </svg>
              {likeCount}
            </button>
            <button className="share-btn" onClick={share}>
              Bagikan
            </button>
            {socials.map(({ label, href, Icon }) => (
              <a
                key={label}
                className="social-chip"
                href={href}
                target="_blank"
                rel="noreferrer"
                title={`Bagikan ke ${label}`}
              >
                <Icon size={15} />
                <span>{label}</span>
              </a>
            ))}
          </div>

          <div className="post-body">
            {post.content.split(/\n\n+/).map((p, i) => {
              const img = NEWS_IMAGE_BLOCK.exec(p.trim());
              if (img) {
                return (
                  <figure key={i}>
                    <img src={img[2]} alt={img[1]} loading="lazy" />
                    {img[1] && <figcaption>{img[1]}</figcaption>}
                  </figure>
                );
              }
              return <p key={i}>{p}</p>;
            })}
          </div>

          <section className="comments">
            <h2>Komentar ({comments.length})</h2>
            <div className="comment-list">
              {comments.length === 0 && (
                <StateNotice
                  tone="empty"
                  glyph="comment"
                  compact
                  title="Belum ada komentar"
                  message="Jadilah yang pertama menanggapi berita ini."
                />
              )}
              {topLevel.map((c) => {
                const replies = comments.filter((r) => r.parentId === c.id);
                return (
                  <div className="comment" key={c.id}>
                    <div className="comment-head">
                      <img
                        className="comment-avatar"
                        src={c.official ? '/team/founder.jpg' : newsAvatarUrl(c.author)}
                        alt=""
                        loading="lazy"
                      />
                      <AuthorName name={c.author} official={c.official} avatar={false} />
                      <span>{formatRelativeTime(c.createdAt)}</span>
                    </div>
                    <p>{c.content}</p>
                    <button className="reply-link" onClick={() => focusCommentForm(c)}>
                      Balas
                    </button>
                    {replies.length > 0 && (
                      <div className="replies">
                        {replies.map((r) => (
                          <div className="reply" key={r.id}>
                            <div className="comment-head">
                              <img
                                className="comment-avatar sm"
                                src={r.official ? '/team/founder.jpg' : newsAvatarUrl(r.author)}
                                alt=""
                                loading="lazy"
                              />
                              <AuthorName name={r.author} official={r.official} avatar={false} />
                              <span>{formatRelativeTime(r.createdAt)}</span>
                            </div>
                            <p>{r.content}</p>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
            <div className="comment-form" ref={commentFormRef}>
              {adminActive && (
                <div className="admin-banner">
                  <img src="/team/founder.jpg" alt="" />
                  <span>
                    Membalas sebagai <strong>Haekal Saputra</strong>
                    <em className="official-tag">XySpace</em>
                  </span>
                  <button
                    type="button"
                    title="Matikan mode tim di perangkat ini"
                    onClick={() => {
                      setAdminToken('');
                      setAdminTokenState('');
                      clearStoredGoogleIdToken();
                      setGoogleToken(null);
                    }}
                  >
                    ×
                  </button>
                </div>
              )}
              {adminEligible && !adminActive && (
                <div className="admin-setup">
                  <p>
                    Login founder terdeteksi. Masuk dengan Google untuk membalas
                    sebagai tim — tanpa token manual:
                  </p>
                  <button
                    type="button"
                    className="btn primary"
                    onClick={() => beginGoogleLogin(window.location.pathname)}
                  >
                    Lanjutkan dengan Google
                  </button>
                  <details className="admin-token-fallback">
                    <summary>Cara lama: tempel ADMIN_TOKEN</summary>
                    <p>Token tersimpan hanya di perangkat ini.</p>
                    <div className="admin-setup-row">
                      <input
                        type="password"
                        placeholder="ADMIN_TOKEN"
                        value={adminDraft}
                        onChange={(e) => setAdminDraft(e.target.value)}
                      />
                      <button
                        type="button"
                        className="btn primary"
                        disabled={!adminDraft.trim()}
                        onClick={() => {
                          setAdminToken(adminDraft);
                          setAdminTokenState(adminDraft.trim());
                          setAdminDraft('');
                        }}
                      >
                        Aktifkan
                      </button>
                    </div>
                  </details>
                </div>
              )}
              {replyTo && (
                <div className="reply-banner">
                  <span>
                    Membalas <AuthorName name={replyTo.author} official={replyTo.official} />
                  </span>
                  <button onClick={() => setReplyTo(null)}>×</button>
                </div>
              )}
              <textarea
                placeholder="Tulis komentar…"
                maxLength={1000}
                rows={3}
                value={commentText}
                onChange={(e) => setCommentText(e.target.value)}
              />
              {notice && <p className="muted">{notice}</p>}
              <button
                className="btn primary"
                disabled={busy || commentText.trim().length < 2}
                onClick={submitComment}
              >
                {busy ? 'Mengirim…' : 'Kirim komentar'}
              </button>
            </div>
          </section>

          <section className="subscribe-box">
            <h2>Berita lewat email</h2>
            <p className="muted">Artikel baru dikirim langsung ke email kamu.</p>
            <div className="subscribe-row">
              <input
                type="email"
                placeholder="alamat@email.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
              <button className="btn primary" onClick={subscribe} disabled={subBusy}>
                {subBusy ? 'Mendaftar…' : 'Langganan'}
              </button>
            </div>
          </section>
        </article>
      )}
      {data && (
        <button
          type="button"
          className="lompat-btn"
          onClick={() =>
            window.scrollTo({
              top: diDasar ? 0 : document.documentElement.scrollHeight,
              behavior: 'smooth',
            })
          }
          title={diDasar ? 'Kembali ke atas' : 'Lompat ke paling bawah'}
          aria-label={diDasar ? 'Kembali ke atas' : 'Lompat ke paling bawah'}
        >
          {diDasar ? (
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M12 19V5" />
              <path d="M5 12l7-7 7 7" />
            </svg>
          ) : (
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M12 5v14" />
              <path d="M19 12l-7 7-7-7" />
            </svg>
          )}
        </button>
      )}
    </main>
  );
}
