/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './src/**/*.java',      // Scan all Java files in the src directory
    './src/**/*.{html,js}',
    './templates/**/*.html',
  ],
  theme: {
    extend: {},
  },
  plugins: [],
}
