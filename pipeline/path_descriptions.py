"""Display names and short descriptions for each career path.

The descriptions are embedded and blended with the mean of the path's node
vectors to form the path vector used for career matching. They are written in
plain student language ("I want to build apps for banks") so that free-text
interests land near the right path. They were drafted with an LLM and edited by
hand; `build_assets.py --regenerate-descriptions` can redraft them with Gemini.
"""

# path id -> (display name, description)
PATHS = {
    "ai-agents": ("AI Agents", "Build autonomous AI agents that use large language models, tools, memory and planning to complete tasks; agent frameworks, tool calling, MCP, multi-agent systems."),
    "ai-data-scientist": ("AI & Data Scientist", "Use statistics, machine learning and data analysis to find insights and build predictive models; Python, maths, experimentation, data science career."),
    "ai-engineer": ("AI Engineer", "Build products on top of AI models: LLM APIs, prompt engineering, embeddings, vector databases, RAG, chatbots and AI-powered apps."),
    "ai-product-builder": ("AI Product Builder", "Turn AI ideas into shipped products quickly using no-code and AI tools, prototyping, product thinking and launching AI apps."),
    "ai-red-teaming": ("AI Red Teaming", "Attack and test AI systems for safety and security: prompt injection, jailbreaks, adversarial testing, responsible AI evaluation."),
    "android": ("Android Developer", "Build native mobile apps for Android phones with Kotlin, Jetpack Compose, Android Studio; mobile banking apps, consumer apps, Play Store."),
    "angular": ("Angular", "Build large web front-ends with the Angular framework and TypeScript: components, RxJS, routing, forms, enterprise web apps."),
    "api-design": ("API Design", "Design clean, secure and well documented REST, GraphQL and gRPC APIs that other developers and apps integrate with."),
    "aspnet-core": ("ASP.NET Core", "Build web APIs and backend services with C#, .NET and ASP.NET Core, common in banks and enterprise software."),
    "aws": ("AWS", "Deploy and run applications on Amazon Web Services cloud: EC2, S3, Lambda, IAM, networking, cloud certifications."),
    "backend": ("Backend Developer", "Build the server side of web and mobile apps: APIs, databases, authentication, business logic, payments and banking systems, scalability."),
    "backend-beginner": ("Backend (Beginner)", "A gentle first path into server-side programming: one language, a database, building and deploying a simple API."),
    "bi-analyst": ("BI Analyst", "Turn business data into dashboards and reports with SQL, Power BI, Tableau and Excel to support decisions in companies."),
    "blockchain": ("Blockchain Developer", "Build decentralised apps and smart contracts with Solidity, Ethereum, Web3, crypto wallets and DeFi."),
    "c": ("C Programming", "Learn the C language: pointers, memory, compilers, systems and embedded programming."),
    "claude-code": ("Claude Code", "Use Claude Code, an AI coding agent in the terminal, to read, write and refactor code faster."),
    "cloudflare": ("Cloudflare", "Build and secure websites and serverless apps on Cloudflare: CDN, Workers, DNS, edge computing."),
    "computer-science": ("Computer Science", "Core computer science fundamentals: algorithms, data structures, complexity, operating systems, networking and theory for interviews."),
    "cpp": ("C++", "Learn C++ for high-performance software, games, systems programming and competitive programming."),
    "css": ("CSS", "Style web pages with CSS: layouts, flexbox, grid, responsive design, animations."),
    "cyber-security": ("Cyber Security", "Protect systems, networks and data from hackers: ethical hacking, security operations, incident response, penetration testing, banking security."),
    "data-analyst": ("Data Analyst", "Clean, analyse and visualise data with Excel, SQL, Python and statistics to answer business questions."),
    "data-engineer": ("Data Engineer", "Build data pipelines, warehouses and ETL with SQL, Python, Spark, Airflow and the cloud so data is reliable for analytics and AI."),
    "datastructures-and-algorithms": ("Data Structures & Algorithms", "Arrays, linked lists, trees, graphs, sorting, searching and problem solving for coding interviews."),
    "design-system": ("Design System", "Create reusable UI components, tokens and guidelines so product teams design and build consistently."),
    "devops": ("DevOps Engineer", "Automate building, testing and deploying software: Linux, CI/CD, Docker, Kubernetes, cloud, monitoring and infrastructure as code."),
    "devops-beginner": ("DevOps (Beginner)", "A first step into DevOps: Linux basics, scripting, version control, a CI pipeline and deploying an app."),
    "devrel": ("Developer Relations", "Be the bridge between a tech company and developers: technical content, talks, community, documentation and demos."),
    "devsecops": ("DevSecOps", "Build security into the software delivery pipeline: secure CI/CD, scanning, secrets, cloud and container security."),
    "django": ("Django", "Build web applications and APIs in Python with the Django framework, ORM and admin."),
    "docker": ("Docker", "Package and run applications in containers with Docker, images, volumes and compose."),
    "elasticsearch": ("Elasticsearch", "Search and analyse large volumes of data with Elasticsearch, indexing and queries."),
    "engineering-manager": ("Engineering Manager", "Lead software teams: people management, hiring, delivery, technical strategy and team culture."),
    "flutter": ("Flutter", "Build cross-platform mobile apps for Android and iOS from one codebase with Flutter and Dart."),
    "forward-deployed-engineer": ("Forward Deployed Engineer", "Work directly with customers to integrate and deploy a company's software and AI solutions in the field."),
    "frontend": ("Frontend Developer", "Build the user interface of websites and web apps with HTML, CSS, JavaScript, React and responsive design."),
    "frontend-beginner": ("Frontend (Beginner)", "A first step into web development: HTML, CSS, a little JavaScript and publishing a website."),
    "full-stack": ("Full Stack Developer", "Build complete web applications end to end: frontend, backend, databases and deployment."),
    "game-developer": ("Game Developer", "Make video games with engines like Unity and Unreal: gameplay programming, graphics, physics and maths."),
    "git-github": ("Git & GitHub", "Version control with Git and collaborating on GitHub: branches, pull requests, open source."),
    "git-github-beginner": ("Git & GitHub (Beginner)", "Your first steps with Git and GitHub: commits, branches and pushing code."),
    "golang": ("Go", "Build fast backend services, CLIs and cloud tools with the Go programming language."),
    "graphql": ("GraphQL", "Design and consume GraphQL APIs: schemas, queries, mutations and resolvers."),
    "html": ("HTML", "Structure web pages with semantic HTML, forms and accessibility."),
    "ios": ("iOS Developer", "Build native apps for iPhone and iPad with Swift, SwiftUI and Xcode for the App Store."),
    "java": ("Java", "Learn Java for enterprise backends, Android and banking systems: OOP, JVM, collections, Spring."),
    "javascript": ("JavaScript", "The programming language of the web: syntax, DOM, async, modules, used on frontend and backend."),
    "kotlin": ("Kotlin", "Learn the Kotlin language used for Android apps and JVM backends: syntax, coroutines, OOP and functional style."),
    "kubernetes": ("Kubernetes", "Run and scale containerised applications with Kubernetes clusters, pods, services and deployments."),
    "laravel": ("Laravel", "Build web applications in PHP with the Laravel framework."),
    "leetcode": ("LeetCode Patterns", "Practise coding interview problems by pattern: two pointers, sliding window, dynamic programming, graphs."),
    "linux": ("Linux", "Use and administer Linux: command line, shell, processes, permissions, networking and servers."),
    "machine-learning": ("Machine Learning Engineer", "Build and train machine learning models: maths, Python, scikit-learn, deep learning, model evaluation and deployment."),
    "mlops": ("MLOps Engineer", "Deploy, monitor and maintain machine learning models in production with pipelines, CI/CD and cloud."),
    "mongodb": ("MongoDB", "Store and query data with the MongoDB NoSQL document database."),
    "network-engineer": ("Network Engineer", "Design, build and maintain computer networks: routing, switching, TCP/IP, firewalls, Cisco, telecoms and ISPs."),
    "nextjs": ("Next.js", "Build full-stack React web applications with Next.js, server rendering and routing."),
    "nodejs": ("Node.js", "Build backend servers and APIs with JavaScript on Node.js, Express and npm."),
    "openclaw": ("OpenClaw", "Set up and use OpenClaw, an open-source personal AI assistant and agent platform."),
    "php": ("PHP", "Build server-side web applications and websites with PHP."),
    "postgresql-dba": ("PostgreSQL DBA", "Administer PostgreSQL databases: installation, SQL, performance tuning, backups, replication and security."),
    "power-bi": ("Power BI", "Build interactive business dashboards and reports with Microsoft Power BI and DAX."),
    "product-design": ("Product Designer", "Design digital products end to end: user research, UX, UI, prototyping and design systems."),
    "product-manager": ("Product Manager", "Decide what a tech product should do and why: user needs, roadmaps, metrics, working with engineers and business."),
    "prompt-engineering": ("Prompt Engineering", "Write effective prompts for large language models: techniques, structure, evaluation."),
    "python": ("Python", "Learn the Python language for scripting, automation, web backends, data and AI."),
    "python-data-analysis": ("Python Data Analysis", "Analyse data in Python with pandas, NumPy, Jupyter and plotting libraries."),
    "qa": ("QA Engineer", "Test software to ensure quality: manual testing, test automation, Selenium, test plans and bug tracking."),
    "r": ("R Programming", "Use the R language for statistics, data analysis and visualisation."),
    "react": ("React", "Build interactive web user interfaces with the React library: components, hooks, state."),
    "react-native": ("React Native", "Build cross-platform mobile apps for Android and iOS with React Native and JavaScript."),
    "redis": ("Redis", "Use Redis for caching, sessions, queues and fast in-memory data storage."),
    "ruby": ("Ruby", "Learn the Ruby programming language."),
    "ruby-on-rails": ("Ruby on Rails", "Build web applications quickly with Ruby on Rails."),
    "rust": ("Rust", "Write safe, fast systems software with the Rust language: ownership, borrowing, concurrency."),
    "scala": ("Scala", "Functional and object-oriented programming on the JVM with Scala, used in big data."),
    "server-side-game-developer": ("Server-side Game Developer", "Build the backend of online multiplayer games: networking, servers, matchmaking and scaling."),
    "shell-bash": ("Shell / Bash", "Automate tasks with shell scripting and the Bash command line."),
    "software-architect": ("Software Architect", "Design the high-level structure of large software systems: architecture patterns, trade-offs, leadership."),
    "software-design-architecture": ("Software Design & Architecture", "Clean code, design principles, design patterns and architectural styles for maintainable software."),
    "spring-boot": ("Spring Boot", "Build Java backend services and APIs with Spring Boot, common in banks and enterprises."),
    "sql": ("SQL", "Query and manage relational databases with SQL: joins, aggregation, indexes, transactions."),
    "swift-ui": ("SwiftUI", "Build user interfaces for Apple platforms with SwiftUI."),
    "system-design": ("System Design", "Design scalable distributed systems: load balancing, caching, databases, queues, for senior interviews."),
    "technical-writer": ("Technical Writer", "Write clear documentation, guides and API docs that help people use technology."),
    "terraform": ("Terraform", "Provision cloud infrastructure as code with Terraform."),
    "typescript": ("TypeScript", "Add static types to JavaScript with TypeScript for safer, larger codebases."),
    "ux-design": ("UX Designer", "Design user experiences: user research, usability, wireframes, prototypes and interaction design."),
    "vibe-coding": ("Vibe Coding", "Build software by directing AI coding tools with natural language."),
    "vue": ("Vue.js", "Build web user interfaces with the Vue.js framework."),
    "wordpress": ("WordPress", "Build and manage websites with WordPress: themes, plugins and hosting."),
}


def display_name(path_id: str) -> str:
    if path_id in PATHS:
        return PATHS[path_id][0]
    return path_id.replace("-", " ").title()


def description(path_id: str) -> str:
    if path_id in PATHS:
        return PATHS[path_id][1]
    return f"Skills and topics for {display_name(path_id)}."
