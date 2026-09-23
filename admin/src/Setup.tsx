import { useState } from 'react'
import { startAdminSetup, confirmAdminSetup, logoutAdmin } from './api'
import type { SessionStatus } from './api'

export default function Setup({onComplete}:{onComplete:(status:SessionStatus)=>void}){
  const [username,setUsername]=useState(''),[password,setPassword]=useState(''),[confirm,setConfirm]=useState('')
  const [pending,setPending]=useState<Awaited<ReturnType<typeof startAdminSetup>>|null>(null)
  const [result,setResult]=useState<SessionStatus|null>(null)
  const [busy,setBusy]=useState(false),[error,setError]=useState('')
  const begin=async(e:React.FormEvent)=>{
    e.preventDefault();if(busy)return
    if(password!==confirm){setError('Konfirmasi password belum sama.');return}
    setBusy(true);setError('')
    try{setPending(await startAdminSetup(username,password));setPassword('');setConfirm('')}
    catch(e){setError(String(e))}finally{setBusy(false)}
  }
  const activate=async()=>{
    if(busy)return
    setBusy(true);setError('')
    try{setResult(await confirmAdminSetup());setPending(null)}
    catch(e){setError(String(e))}finally{setBusy(false)}
  }
  return <div className="login-wrap"><div className="login-card" style={{maxWidth:560}}>
    <h1>{result?'Akun admin siap':'Siapkan akun admin'}</h1>
    <p className="muted">{result?'Login Google sudah dimatikan. Gunakan username, password, dan Cloudflare Turnstile.':'Google hanya untuk membuktikan kepemilikan saat setup. Setelah setup, login cukup memakai username, password, dan Turnstile.'}</p>
    {error&&<p className="error" role="alert">{error}</p>}
    {result?<>
      <h3>Setup selesai</h3>
      <p>Simpan username dan password di password manager. Cloudflare Turnstile akan memverifikasi browser saat login.</p>
      <button className="btn primary block" onClick={()=>onComplete(result)}>Buka dashboard</button>
    </>:pending?<>
      <h3>Konfirmasi setup</h3>
      <p>Username <strong>{username}</strong> sudah disiapkan. Klik tombol di bawah dalam waktu 10 menit untuk mengaktifkan akun.</p>
      <p className="muted">Berlaku sampai {new Date(pending.expiresAt).toLocaleTimeString('id-ID')}.</p>
      <button className="btn primary block" disabled={busy} onClick={()=>void activate()}>{busy?'Mengaktifkan...':'Aktifkan akun & matikan login Google'}</button>
      <button className="btn" type="button" disabled={busy} onClick={()=>setPending(null)} style={{marginTop:8}}>Mulai ulang setup</button>
    </>:<form onSubmit={begin}>
      <div className="field"><label htmlFor="setup-user">Username admin</label><input id="setup-user" value={username} onChange={e=>setUsername(e.target.value)} autoComplete="username" autoCapitalize="none" pattern="[a-z0-9][a-z0-9._-]{2,31}" minLength={3} maxLength={32} required disabled={busy}/></div>
      <div className="field"><label htmlFor="setup-password">Password baru (14–128 karakter)</label><input id="setup-password" type="password" autoComplete="new-password" value={password} onChange={e=>setPassword(e.target.value)} minLength={14} maxLength={128} required disabled={busy}/></div>
      <div className="field"><label htmlFor="setup-confirm">Ulangi password</label><input id="setup-confirm" type="password" autoComplete="new-password" value={confirm} onChange={e=>setConfirm(e.target.value)} required disabled={busy}/></div>
      <p className="muted">Gunakan password unik dari password manager. Password tidak perlu dikirim ke chat.</p>
      <button className="btn primary block" disabled={busy}>{busy?'Menyiapkan...':'Lanjutkan setup'}</button>
    </form>}
    {!result&&<button className="btn" style={{marginTop:12}} disabled={busy} onClick={()=>{void logoutAdmin().catch(e=>setError(String(e)))}}>Keluar</button>}
  </div></div>
}
