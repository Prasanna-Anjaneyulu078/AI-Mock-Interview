import Editor from '@monaco-editor/react';
import './index.css';

function CodeEditor({ value, onChange, language, readOnly }) {
  return (
    <div className="code-editor-wrapper">
      <Editor
        height="300px"
        language={language || 'javascript'}
        theme="light"
        value={value}
        onChange={onChange}
        options={{
          readOnly: readOnly || false,
          minimap: { enabled: false },
          fontSize: 14,
          fontFamily: "monospace",
          wordWrap: 'on',
          scrollBeyondLastLine: false,
          automaticLayout: true,
          tabSize: 2,
          bracketPairColorization: { enabled: true },
          padding: { top: 12 },
        }}
      />
    </div>
  );
}

export default CodeEditor;
