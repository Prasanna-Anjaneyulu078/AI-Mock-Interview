import { createContext, useState, useEffect } from 'react';

export const ThemeContext = createContext();

// Only Light and Dark are supported. Any legacy value (e.g. 'system') is
// migrated to the default 'light' so the UI is never in an undefined state.
const VALID_THEMES = ['light', 'dark'];

export const ThemeProvider = ({ children }) => {
  const [theme, setTheme] = useState(() => {
    const stored = localStorage.getItem('theme');
    return VALID_THEMES.includes(stored) ? stored : 'light';
  });

  useEffect(() => {
    const root = document.documentElement;
    root.setAttribute('data-theme', theme);
    localStorage.setItem('theme', theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme((prev) => (prev === 'light' ? 'dark' : 'light'));
  };

  return (
    <ThemeContext.Provider value={{ theme, setTheme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  );
};
