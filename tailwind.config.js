/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './app/**/*.{js,ts,jsx,tsx}',
    './components/**/*.{js,ts,jsx,tsx}'
  ],
  theme: {
    extend: {
      colors: {
        dark: {
          bg: '#0A0A14',
          surface: '#12121F',
          border: '#2A2A42'
        },
        accent: {
          orange: '#FF6B35',
          pink: '#FF1B8D',
          purple: '#9D4EDD',
          blue: '#5390D9',
          cyan: '#5390D9'
        }
      },
      backgroundImage: {
        'gradient-brand': 'linear-gradient(135deg, #FF6B35 0%, #FF1B8D 35%, #9D4EDD 65%, #5390D9 100%)',
        'gradient-cta': 'linear-gradient(135deg, #FF6B35 0%, #FF1B8D 50%, #9D4EDD 100%)',
        'gradient-dark': 'linear-gradient(180deg, #0A0A14 0%, #12121F 50%, #0A0A14 100%)'
      },
      animation: {
        float: 'float 8s ease-in-out infinite',
        'float-delayed': 'float 10s ease-in-out 2s infinite',
        'pulse-glow': 'pulse-glow 4s ease-in-out infinite'
      },
      keyframes: {
        float: {
          '0%, 100%': { transform: 'translate(0, 0) scale(1)' },
          '50%': { transform: 'translate(24px, -32px) scale(1.05)' }
        },
        'pulse-glow': {
          '0%, 100%': { opacity: '0.35' },
          '50%': { opacity: '0.65' }
        }
      }
    }
  },
  plugins: []
}
