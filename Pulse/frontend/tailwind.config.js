/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        // Surfaces: deep midnight blues
        night: '#050B1F',
        panel: '#0A1331',
        raised: '#101C45',
        edge: '#1B2A5E',
        // Text
        ink: { DEFAULT: '#E9EFFF', soft: '#A6B5E6', mute: '#6F80B8' },
        // Electric blue
        volt: { DEFAULT: '#2E6BFF', bright: '#5B8DFF', ice: '#BFD3FF', deep: '#1B3FC4' },
        // Pearlescent accents
        pearl: { navy: '#14246B', sheen: '#8FB2FF', lilac: '#B7A8FF', aqua: '#6FE0F2' },
        // Status
        ok: '#3DDBD0',
        live: '#5B8DFF',
        hold: '#69CBFF',
        wait: '#A592FF',
        bad: '#FF7390',
      },
      fontFamily: {
        display: ['"Bricolage Grotesque Variable"', 'system-ui', 'sans-serif'],
        body: ['"Instrument Sans Variable"', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono Variable"', 'ui-monospace', 'monospace'],
      },
      boxShadow: {
        glow: '0 0 28px -6px rgba(46, 107, 255, 0.55)',
        lift: '0 24px 60px -24px rgba(2, 8, 30, 0.9), 0 0 0 1px rgba(143, 178, 255, 0.06)',
      },
      keyframes: {
        drawerIn: { from: { transform: 'translateX(24px)', opacity: '0' }, to: { transform: 'none', opacity: '1' } },
        fadeIn: { from: { opacity: '0' }, to: { opacity: '1' } },
        flow: { to: { strokeDashoffset: '-24' } },
        ping2: { '0%': { transform: 'scale(1)', opacity: '.7' }, '100%': { transform: 'scale(2.4)', opacity: '0' } },
      },
      animation: {
        drawerIn: 'drawerIn .22s ease-out',
        fadeIn: 'fadeIn .18s ease-out',
        flow: 'flow 1.4s linear infinite',
        ping2: 'ping2 1.6s ease-out infinite',
      },
    },
  },
  plugins: [],
}
