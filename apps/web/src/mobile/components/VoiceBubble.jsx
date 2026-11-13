export default function VoiceBubble() {
  return (
    <svg className="splash-voice-bubble" viewBox="0 0 180 100" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id="splash-bubble-surface" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#fff" />
          <stop offset="0.55" stopColor="#f3f5f7" />
          <stop offset="1" stopColor="#c8cfd6" />
        </linearGradient>
      </defs>
      <path d="M38 12H142Q171 12 171 40V54Q171 78 148 78L150 93L134 78H38Q9 78 9 50V40Q9 12 38 12Z" fill="url(#splash-bubble-surface)" stroke="#17191c" strokeWidth="2" strokeLinejoin="round" />
      <path d="M38 15H142Q165 15 167 36" fill="none" stroke="#fff" strokeWidth="1.5" strokeLinecap="round" />
      <g stroke="#203f62" strokeWidth="4" strokeLinecap="round">
        {[8, 25, 40, 17, 33, 21, 12, 27, 8].map((height, i) => (
          <path key={i} d={`M${43 + i * 11.5} ${45 - height / 2}v${height}`} />
        ))}
      </g>
    </svg>
  );
}
