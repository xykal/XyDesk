export function ConnectionMorph(){
 const timing={dur:'6s',repeatCount:'indefinite',keyTimes:'0;.22;.33;.55;.66;.88;1',calcMode:'spline' as const,keySplines:'.4 0 .2 1;.4 0 .2 1;.4 0 .2 1;.4 0 .2 1;.4 0 .2 1;.4 0 .2 1'};
 return <div className="connection-morph" aria-hidden="true">
  <svg className="connection-morph-motion" viewBox="0 0 160 140" fill="none" stroke="currentColor" strokeWidth="3">
   <rect x="58" y="20" width="44" height="88" rx="9">
    <animate attributeName="x" values="58;58;18;18;36;36;58" {...timing}/>
    <animate attributeName="y" values="20;20;28;28;18;18;20" {...timing}/>
    <animate attributeName="width" values="44;44;124;124;88;88;44" {...timing}/>
    <animate attributeName="height" values="88;88;76;76;98;98;88" {...timing}/>
    <animate attributeName="rx" values="9;9;6;6;10;10;9" {...timing}/>
   </rect>
   <path d="M70 28H90" strokeLinecap="round"><animate attributeName="opacity" values="1;1;0;0;0;0;1" {...timing}/></path>
   <path d="M80 106V121M60 123H100" strokeLinecap="round" opacity="0"><animate attributeName="opacity" values="0;0;1;1;0;0;0" {...timing}/></path>
   <circle cx="80" cy="100" r="1.5" fill="currentColor" stroke="none"><animate attributeName="cy" values="100;100;97;97;108;108;100" {...timing}/></circle>
  </svg>
  <svg className="connection-morph-static" viewBox="0 0 160 140" fill="none" stroke="currentColor" strokeWidth="3"><rect x="58" y="20" width="44" height="88" rx="9"/><path d="M70 28H90" strokeLinecap="round"/></svg>
 </div>;
}
