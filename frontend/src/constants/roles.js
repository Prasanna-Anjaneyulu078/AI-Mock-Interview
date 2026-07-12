import { FaJava, FaPython, FaReact } from 'react-icons/fa';
import { BsLightningFill, BsWindowStack, BsDiagram3, BsCloudFill, BsGraphUp, BsRobot } from 'react-icons/bs';

export const INTERVIEW_ROLES = [
  {
    id: 'JAVA_BACKEND_DEVELOPER',
    title: 'Java Backend Developer',
    icon: FaJava,
    description: 'Core Java, Spring Boot, REST APIs, Microservices',
    topics: ['Core Java', 'OOP', 'Collections', 'Streams', 'JVM', 'Multithreading', 'Spring Boot', 'REST APIs', 'Hibernate/JPA', 'Security', 'Microservices', 'SQL']
  },
  {
    id: 'PYTHON_DEVELOPER',
    title: 'Python Developer',
    icon: FaPython,
    description: 'Python fundamentals, Flask, Django, FastAPI',
    topics: ['Python Fundamentals', 'OOP', 'Decorators', 'Generators', 'Iterators', 'Context Managers', 'Asyncio', 'Flask', 'Django', 'FastAPI', 'SQLAlchemy', 'Testing', 'Performance Optimization']
  },
  {
    id: 'FRONTEND_DEVELOPER',
    title: 'Frontend Developer',
    icon: FaReact,
    description: 'HTML, CSS, JavaScript, TypeScript, React',
    topics: ['HTML', 'CSS', 'JavaScript', 'TypeScript', 'React', 'Hooks', 'State Management', 'Routing', 'API Integration', 'Performance Optimization']
  },

  {
    id: 'CS_FUNDAMENTALS',
    title: 'CS Fundamentals',
    icon: BsWindowStack,
    description: 'OOP, DBMS, SQL, OS, Computer Networks',
    topics: ['OOP', 'DBMS', 'SQL', 'Transactions', 'Indexing', 'Normalization', 'Operating Systems', 'Memory Management', 'Scheduling', 'Deadlocks', 'Computer Networks', 'TCP/IP', 'DNS', 'HTTP']
  },
  {
    id: 'SYSTEM_DESIGN',
    title: 'System Design',
    icon: BsDiagram3,
    description: 'LLD, HLD, Database Design, Scalability',
    topics: ['Database Design', 'ER Diagrams', 'Schema Design', 'SQL Optimization', 'LLD', 'SOLID Principles', 'Design Patterns', 'UML', 'HLD', 'Scalability', 'Load Balancing', 'Caching', 'Distributed Systems', 'Microservices', 'API Design']
  },
  {
    id: 'DEVOPS_ENGINEER',
    title: 'DevOps Engineer',
    icon: BsCloudFill,
    description: 'Linux, Docker, Kubernetes, CI/CD, AWS',
    topics: ['Linux', 'Docker', 'Kubernetes', 'CI/CD', 'AWS', 'Terraform', 'Monitoring']
  },
  {
    id: 'DATA_ANALYST',
    title: 'Data Analyst',
    icon: BsGraphUp,
    description: 'SQL, Excel, Power BI, Statistics, Python',
    topics: ['SQL', 'Excel', 'Power BI', 'Statistics', 'Python', 'Data Visualization']
  },
  {
    id: 'AI_ML_ENGINEER',
    title: 'AI / ML Engineer',
    icon: BsRobot,
    description: 'Machine Learning, Deep Learning, NLP, Computer Vision',
    topics: ['Machine Learning', 'Deep Learning', 'NLP', 'Computer Vision', 'TensorFlow', 'PyTorch', 'MLOps', 'LLMs']
  }
];

export default INTERVIEW_ROLES;
