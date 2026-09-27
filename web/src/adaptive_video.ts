export type VideoSample={recentLossPct?:number;jitterBufferMs?:number;rttMs?:number;decodeMs?:number;width?:number;height?:number;fps?:number};
// Start below a ~2 Mbps access link, leaving room for audio/transport overhead.
// Encoder restarts are expensive: back off promptly, probe up slowly. Never resize.
export class AdaptiveVideo {
 target=1; private stable=0; private changed=0; private baselineRtt=Infinity;
 reset(target=1){this.target=Math.max(1,Math.min(50,target));this.stable=0;this.changed=0;this.baselineRtt=Infinity;}
 update(s:VideoSample,ceiling:number,now:number):number|null {
  if(!Number.isFinite(s.recentLossPct)||!Number.isFinite(s.jitterBufferMs)){this.stable=0;return null;}
  const cap=Math.max(1,Math.min(50,Number.isFinite(ceiling)?ceiling:1));
  const rtt=s.rttMs??0;
  if(rtt>0&&Number.isFinite(rtt))this.baselineRtt=Math.min(this.baselineRtt,rtt);
  // A high but stable geographic RTT is not itself proof of congestion.
  const queuedRtt=rtt>this.baselineRtt+80&&rtt>this.baselineRtt*1.4;
  const congested=s.recentLossPct!>2||s.jitterBufferMs!>90||queuedRtt;
  this.stable=congested?0:this.stable+1;
  if(now-this.changed<(congested?6000:20000))return null;
  let next=Math.min(this.target,cap);
  if(congested)next=Math.max(1,Math.floor(next*.7));
  else if(this.stable>=30){next=Math.min(cap,next+1);this.stable=0;}
  if(next===this.target)return null;
  this.target=next;this.changed=now;return next;
 }
}
