package com.aimall.user;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {

    public record UserRow(long id, String username, String passwordHash, String nickname) {
    }

    private static final RowMapper<UserRow> MAPPER = (rs, i) -> new UserRow(
            rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"), rs.getString("nickname"));

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRow> findByUsername(String username) {
        return jdbc.sql("SELECT id, username, password_hash, nickname FROM t_user WHERE username = :u")
                .param("u", username).query(MAPPER).optional();
    }

    public Optional<UserRow> findById(long id) {
        return jdbc.sql("SELECT id, username, password_hash, nickname FROM t_user WHERE id = :id")
                .param("id", id).query(MAPPER).optional();
    }

    public long insert(String username, String passwordHash, String nickname) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO t_user(username, password_hash, nickname) VALUES (:u, :p, :n)")
                .param("u", username).param("p", passwordHash).param("n", nickname)
                .update(kh);
        return kh.getKey().longValue();
    }
}
