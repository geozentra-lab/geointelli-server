def test_playground_is_off_by_default(make_client):
    client, _ = make_client()
    assert client.get("/playground").status_code == 404


def test_playground_serves_the_page_when_enabled(make_client):
    client, _ = make_client(playground_enabled=True)

    response = client.get("/playground")

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/html")
    assert "AiZentra playground" in response.text
    # The page holds no secret; the user types the internal token.
    assert "local-dev-internal-token" not in response.text
