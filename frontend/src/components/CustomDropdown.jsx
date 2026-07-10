import React, { useState, useRef, useEffect } from 'react';
import { BsChevronDown } from 'react-icons/bs';
import './CustomDropdown.css';

const CustomDropdown = ({ options, value, onChange, placeholder = 'Select an option', searchable = false }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [focusedIndex, setFocusedIndex] = useState(-1);
  const dropdownRef = useRef(null);

  const filteredOptions = options.filter(option => 
    option.label.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const selectedOption = options.find(opt => opt.value === value);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleKeyDown = (e) => {
    if (!isOpen) {
      if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') {
        e.preventDefault();
        setIsOpen(true);
      }
      return;
    }

    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setFocusedIndex(prev => (prev < filteredOptions.length - 1 ? prev + 1 : prev));
        break;
      case 'ArrowUp':
        e.preventDefault();
        setFocusedIndex(prev => (prev > 0 ? prev - 1 : prev));
        break;
      case 'Enter':
        e.preventDefault();
        if (focusedIndex >= 0 && focusedIndex < filteredOptions.length) {
          onChange(filteredOptions[focusedIndex].value);
          setIsOpen(false);
        }
        break;
      case 'Escape':
        setIsOpen(false);
        break;
      default:
        break;
    }
  };

  const handleSelect = (val) => {
    onChange(val);
    setIsOpen(false);
    setSearchTerm('');
  };

  return (
    <div className="custom-dropdown-container" ref={dropdownRef}>
      <button
        type="button"
        className={`custom-dropdown-trigger ${isOpen ? 'open' : ''}`}
        onClick={() => setIsOpen(!isOpen)}
        onKeyDown={handleKeyDown}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
      >
        <span>{selectedOption ? selectedOption.label : placeholder}</span>
        <BsChevronDown className={`custom-dropdown-icon ${isOpen ? 'open' : ''}`} />
      </button>

      <div className={`custom-dropdown-overlay ${isOpen ? 'open' : ''}`} onClick={() => setIsOpen(false)} />

      <div className={`custom-dropdown-menu ${isOpen ? 'open' : ''}`} role="listbox">
        {searchable && (
          <div className="custom-dropdown-search">
            <input
              type="text"
              placeholder="Search..."
              value={searchTerm}
              onChange={(e) => {
                setSearchTerm(e.target.value);
                setFocusedIndex(-1);
              }}
              onKeyDown={handleKeyDown}
              onClick={(e) => e.stopPropagation()}
            />
          </div>
        )}
        {filteredOptions.length > 0 ? (
          filteredOptions.map((option, index) => (
            <div
              key={option.value}
              role="option"
              aria-selected={value === option.value}
              className={`custom-dropdown-item ${value === option.value ? 'selected' : ''} ${focusedIndex === index ? 'focused' : ''}`}
              onClick={() => handleSelect(option.value)}
              onMouseEnter={() => setFocusedIndex(index)}
            >
              {option.label}
            </div>
          ))
        ) : (
          <div className="custom-dropdown-item" style={{ cursor: 'default', color: '#888' }}>
            No results found
          </div>
        )}
      </div>
    </div>
  );
};

export default CustomDropdown;
