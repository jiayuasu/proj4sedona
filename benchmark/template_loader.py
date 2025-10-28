"""
Template loader for benchmark runner code.
Loads Java and Python templates from the templates/ directory and performs substitutions.
"""

from pathlib import Path


class TemplateLoader:
    """Loads and fills in benchmark templates."""
    
    def __init__(self):
        self.template_dir = Path(__file__).parent / "templates"
    
    def load_template(self, filename):
        """Load a template file."""
        template_path = self.template_dir / filename
        if not template_path.exists():
            raise FileNotFoundError(f"Template not found: {template_path}")
        return template_path.read_text()
    
    def fill_template(self, template_content, **kwargs):
        """Fill in template placeholders with actual values."""
        result = template_content
        for key, value in kwargs.items():
            placeholder = "{" + key + "}"
            result = result.replace(placeholder, str(value))
        return result
    
    def load_java_benchmark_template(self):
        """Load the Java benchmark runner template."""
        return self.load_template("BenchmarkRunner.java")
    
    def load_java_batch_benchmark_template(self):
        """Load the Java batch benchmark runner template."""
        return self.load_template("BatchBenchmarkRunner.java")
    
    def load_python_benchmark_template(self):
        """Load the Python benchmark runner template."""
        return self.load_template("benchmark_runner.py")
    
    def load_python_batch_benchmark_template(self):
        """Load the Python batch benchmark runner template."""
        return self.load_template("batch_benchmark_runner.py")

