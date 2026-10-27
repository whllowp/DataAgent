import React from 'react';
import { createRoot } from 'react-dom/client';
import { SqlStudio } from './SqlStudio.jsx';

document.body.classList.add('da-standalone');
createRoot(document.getElementById('root')).render(<SqlStudio embedded={false} />);
