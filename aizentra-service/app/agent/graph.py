"""The agent loop: the model decides which tools to call; LangGraph runs them and loops back.

START -> agent --(tool calls)--> tools -> agent ... --(no tool calls)--> END
"""

from langchain_core.language_models import BaseChatModel
from langgraph.graph import START, MessagesState, StateGraph
from langgraph.graph.state import CompiledStateGraph
from langgraph.prebuilt import ToolNode, tools_condition

from app.agent.tools import TOOLS


def build_agent(llm: BaseChatModel) -> CompiledStateGraph:
    model = llm.bind_tools(TOOLS)

    async def agent(state: MessagesState) -> dict:
        return {"messages": [await model.ainvoke(state["messages"])]}

    graph = StateGraph(MessagesState)
    graph.add_node("agent", agent)
    graph.add_node("tools", ToolNode(TOOLS))
    graph.add_edge(START, "agent")
    graph.add_conditional_edges("agent", tools_condition)
    graph.add_edge("tools", "agent")
    return graph.compile()


def recursion_limit(max_tool_rounds: int) -> int:
    """LangGraph step limit that allows exactly `max_tool_rounds` tool rounds plus the final answer."""
    return 2 * max_tool_rounds + 2
