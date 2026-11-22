from functools import lru_cache

from pydantic import Field, SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Service settings, read from environment variables or a local .env file."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # OpenAI-compatible model server: Ollama locally, vLLM in production.
    llm_base_url: str = "http://localhost:11434/v1"
    llm_model: str = "qwen3:8b"
    llm_api_key: SecretStr = SecretStr("ollama")
    llm_timeout_seconds: float = 120
    # Qwen3 only: false skips the model's hidden reasoning step. Much faster, but in testing qwen3:1.7b
    # then skipped tools and invented figures, so keep it true for small models.
    llm_thinking: bool = True

    # Spring property-service. Tools call it with the user's own token.
    property_service_url: str = "http://localhost:8081"
    property_service_timeout_seconds: float = 10

    # Shared secret Spring sends in X-Internal-Token; no default, so the service never starts open.
    internal_token: SecretStr

    # Most model <-> tool rounds for one question.
    max_tool_rounds: int = Field(default=4, ge=1, le=10)

    # Serves the temporary test page at /playground. Local development only.
    playground_enabled: bool = False

    @field_validator("internal_token")
    @classmethod
    def _long_enough(cls, value: SecretStr) -> SecretStr:
        if len(value.get_secret_value()) < 16:
            raise ValueError("INTERNAL_TOKEN must be at least 16 characters")
        return value


@lru_cache
def get_settings() -> Settings:
    return Settings()
