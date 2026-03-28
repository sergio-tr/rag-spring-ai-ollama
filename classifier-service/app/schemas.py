"""
Pydantic schemas for API request/response bodies.
All JSON uses camelCase for interoperability with Java/JavaScript clients.
"""
from pydantic import BaseModel, Field


class ClassifyRequest(BaseModel):
    """Request body for POST /classify."""

    query: str
    modelId: str | None = Field(None, description="Model id to use; if omitted, default model is used.")


class ClassifyResponse(BaseModel):
    """Response body for POST /classify."""

    queryType: str


class ModelInfo(BaseModel):
    """Response item for GET /models."""

    id: str
    name: str
    createdAt: str | None = None
    metrics: dict | None = None


class TrainResponse(BaseModel):
    """Response body for POST /train."""

    modelId: str
    name: str
    metrics: dict
