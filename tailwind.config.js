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
  plugins: [
     require('daisyui'),
  ],
  daisyui: {
    themes: ["light", "dim", "cupcake"], // Enable the themes you want to use
  },
}
