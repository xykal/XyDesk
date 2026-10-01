import { useId } from 'react';

const TL_ARM =
  'M 151 113 L 297 113 C 371 187 449 263 521 333 L 382 505 C 286 400 189 296 108 196 C 88 168 111 113 151 113 Z';
const BR_ARM =
  'M 638 483 C 730 586 828 692 916 800 C 940 838 918 908 876 908 L 743 908 C 660 824 580 743 505 665 Z';
const FG_RIBBON =
  'M 736 113 L 876 113 C 922 113 942 171 911 214 C 703 461 485 698 259 908 L 129 908 C 90 908 82 849 115 806 C 322 557 532 324 736 113 Z';
const S_RIDGE = 'M 301 618 C 377 532 519 550 629 474';

export function XyDeskMark({
  size = 30,
  variant = 'header',
}: {
  size?: number;
  variant?: 'splash' | 'header' | 'footer';
}) {
  const uid = useId().replace(/:/g, '');
  const clipId = `xy-clip-${uid}`;
  const strokeGradId = `xy-stroke-${uid}`;
  const sheenGradId = `xy-sheen-${uid}`;

  return (
    <span
      className={`xydesk-mark xydesk-mark-${variant}`}
      style={{ width: size, height: size }}
      aria-hidden="true"
    >
      <img src="/logo.png" alt="XyDesk" width={size} height={size} className="xydesk-mark-img" />
      <svg
        viewBox="0 0 1024 1024"
        width={size}
        height={size}
        className="xydesk-mark-svg"
        focusable="false"
        aria-hidden="true"
      >
        <defs>
          <linearGradient id={strokeGradId} x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#7C3AED" />
            <stop offset="52%" stopColor="#D946EF" />
            <stop offset="100%" stopColor="#C4B5FD" />
          </linearGradient>
          <linearGradient id={sheenGradId} x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="#FFFFFF" stopOpacity="0" />
            <stop offset="35%" stopColor="#F5D0FE" stopOpacity="0.45" />
            <stop offset="50%" stopColor="#FFFFFF" stopOpacity="0.92" />
            <stop offset="65%" stopColor="#C4B5FD" stopOpacity="0.4" />
            <stop offset="100%" stopColor="#FFFFFF" stopOpacity="0" />
          </linearGradient>
          <clipPath id={clipId}>
            <path d={TL_ARM} />
            <path d={BR_ARM} />
            <path d={FG_RIBBON} />
          </clipPath>
        </defs>

        <g className="xydesk-mark-contours" stroke={`url(#${strokeGradId})`} fill="none" strokeLinecap="round" strokeLinejoin="round">
          <path d={FG_RIBBON} pathLength={100} className="xydesk-contour c1" />
          <path d={TL_ARM} pathLength={100} className="xydesk-contour c2" />
          <path d={BR_ARM} pathLength={100} className="xydesk-contour c3" />
          <path d={S_RIDGE} pathLength={100} className="xydesk-contour c4" />
        </g>

        <g clipPath={`url(#${clipId})`}>
          <rect
            x="-420"
            y="-220"
            width="360"
            height="1480"
            fill={`url(#${sheenGradId})`}
            transform="rotate(-28 512 512)"
            className="xydesk-mark-sheen"
          />
        </g>

        <g className="xydesk-mark-glints">
          <path
            d="M 796 132 Q 796 182 846 182 Q 796 182 796 232 Q 796 182 746 182 Q 796 182 796 132 Z"
            fill="#FFFFFF"
            className="xydesk-glint g1"
          />
          <path
            d="M 496 474 Q 496 532 554 532 Q 496 532 496 590 Q 496 532 438 532 Q 496 532 496 474 Z"
            fill="#FFFFFF"
            className="xydesk-glint g2"
          />
          <path
            d="M 226 768 Q 226 816 274 816 Q 226 816 226 864 Q 226 816 178 816 Q 226 816 226 768 Z"
            fill="#FFFFFF"
            className="xydesk-glint g3"
          />
        </g>
      </svg>
    </span>
  );
}
