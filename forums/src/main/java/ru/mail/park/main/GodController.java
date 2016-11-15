package ru.mail.park.main;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.*;


@CrossOrigin
@RestController
public class GodController {
    private static JdbcTemplate jdbcTemplate;

    public GodController(JdbcTemplate jdbcTemplate) {
        GodController.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    @RequestMapping(path = "db/api/clear", method = RequestMethod.POST)
    public ResponseEntity clear() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE user_profile;");
        jdbcTemplate.execute("TRUNCATE TABLE forum;");
        jdbcTemplate.execute("TRUNCATE TABLE thread;");
        jdbcTemplate.execute("TRUNCATE TABLE post;");
        jdbcTemplate.execute("TRUNCATE TABLE following;");
        jdbcTemplate.execute("TRUNCATE TABLE subscription;");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        return ResponseEntity.ok(ResponseBody.ok());
    }

    @Transactional
    @RequestMapping(path = "db/api/status", method = RequestMethod.GET)
    public ResponseEntity status() {
        final int user = jdbcTemplate.queryForObject("SELECT count(*) FROM user_profile;", Integer.class);
        final int thread = jdbcTemplate.queryForObject("SELECT count(*) FROM thread WHERE isDeleted = FALSE;", Integer.class);
        final int forum = jdbcTemplate.queryForObject("SELECT count(*) FROM forum;", Integer.class);
        final int post = jdbcTemplate.queryForObject("SELECT count(*) FROM post WHERE isDeleted = FALSE;", Integer.class);
        return ResponseEntity.ok(ResponseBody.ok(new StatusResponse(user, thread, forum, post)));
    }

    @RequestMapping(path = "db/api/user/create", method = RequestMethod.POST)
    public ResponseEntity createUser(@RequestBody UserCreateRequest request) {
        if (StringUtils.isEmpty(request.email)) {
            return ResponseEntity.ok(ResponseBody.invalid());
        }
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                final PreparedStatement ps = connection.prepareStatement("INSERT INTO user_profile " +
                        "(username, email, name, about, isAnonymous) VALUES (?, ?, ?, ?, ?);",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, request.username);
                ps.setString(2, request.email);
                ps.setString(3, request.name);
                ps.setString(4, request.about);
                ps.setBoolean(5, request.isAnonymous);
                return ps;
            }, keyHolder);
        } catch (DuplicateKeyException e) {
            return ResponseEntity.ok(ResponseBody.userAlreadyExists());
        }
        final UserCreateResponse response = new UserCreateResponse();
        response.id = keyHolder.getKey().intValue();
        response.username = request.username;
        response.email = request.email;
        response.name = request.name;
        response.about = request.about;
        response.isAnonymous = request.isAnonymous;
        return ResponseEntity.ok(ResponseBody.ok(response));
    }

    @Transactional
    @RequestMapping(path = "db/api/forum/create", method = RequestMethod.POST)
    public ResponseEntity createForum(@RequestBody ForumCreateRequest request) {
        if (StringUtils.isEmpty(request.name) || StringUtils.isEmpty(request.short_name) ||
                StringUtils.isEmpty(request.user)) {
            return ResponseEntity.ok(ResponseBody.invalid());
        }
        try {
            jdbcTemplate.update("INSERT INTO forum (name, short_name, user_email) VALUES (?, ?, ?);", request.name,
                    request.short_name, request.user);
        } catch (DuplicateKeyException ignore) {
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        final SqlRowSet set = jdbcTemplate.queryForRowSet("SELECT * FROM forum WHERE name = ? AND short_name = ? " +
                "AND user_email = ?;", request.name, request.short_name, request.user);
        if (!set.next()) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        final ForumDetails details = new ForumDetails();
        details.id = set.getInt("id");
        details.name = set.getString("name");
        details.short_name = set.getString("short_name");
        details.user = set.getString("user_email");
        return ResponseEntity.ok(ResponseBody.ok(details));
    }

    @SuppressWarnings("OverlyComplexBooleanExpression")
    @RequestMapping(path = "db/api/thread/create", method = RequestMethod.POST)
    public ResponseEntity createThread(@RequestBody ThreadCreateRequest request) {
        if (request.isClosed == null || StringUtils.isEmpty(request.forum) || StringUtils.isEmpty(request.title) ||
                StringUtils.isEmpty(request.user) || StringUtils.isEmpty(request.date) ||
                StringUtils.isEmpty(request.message) || StringUtils.isEmpty(request.slug) ) {
            return ResponseEntity.ok(ResponseBody.invalid());
        }
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                final PreparedStatement ps = connection.prepareStatement("INSERT INTO " +
                        "thread (forum, title, slug, message, user_email, creation_time, isClosed, isDeleted) VALUES " +
                        "(?, ?, ?, ?, ?, ?, ?, ?);", Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, request.forum);
                ps.setString(2, request.title);
                ps.setString(3, request.slug);
                ps.setString(4, request.message);
                ps.setString(5, request.user);
                ps.setTimestamp(6, Timestamp.valueOf(request.date));
                ps.setBoolean(7, request.isClosed);
                ps.setBoolean(8, request.isDeleted);
                return ps;
            }, keyHolder);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        final ThreadCreateResponse response = new ThreadCreateResponse();
        response.date = request.date;
        response.forum = request.forum;
        response.id = keyHolder.getKey().intValue();
        response.isClosed = request.isClosed;
        response.isDeleted = request.isDeleted;
        response.message = request.message;
        response.slug = request.slug;
        response.title = request.title;
        response.user = request.user;
        return ResponseEntity.ok(ResponseBody.ok(response));
    }

    @SuppressWarnings({"OverlyComplexBooleanExpression", "MagicNumber"})
    @Transactional
    @RequestMapping(path = "db/api/post/create", method = RequestMethod.POST)
    public ResponseEntity createPost(@RequestBody PostCreateRequest request) {
        if (StringUtils.isEmpty(request.date) || StringUtils.isEmpty(request.forum) ||
                StringUtils.isEmpty(request.user) || StringUtils.isEmpty(request.message) ||
                request.thread == null) {
            return ResponseEntity.ok(ResponseBody.invalid());
        }
        if (!request.isDeleted) {
            jdbcTemplate.update("UPDATE thread SET posts = posts + 1 WHERE id = ?", request.thread);
        }
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                final PreparedStatement ps = connection.prepareStatement("INSERT INTO post " +
                        "(user_email, message, forum, thread_id, parent, creation_time, isApproved, isHighlighted, " +
                        "isEdited, isSpam, isDeleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, request.user);
                ps.setString(2, request.message);
                ps.setString(3, request.forum);
                ps.setInt(4, request.thread);
                ps.setObject(5, request.parent);
                ps.setTimestamp(6, Timestamp.valueOf(request.date));
                ps.setBoolean(7, request.isApproved);
                ps.setBoolean(8, request.isHighlighted);
                ps.setBoolean(9, request.isEdited);
                ps.setBoolean(10, request.isSpam);
                ps.setBoolean(11, request.isDeleted);
                return ps;
            }, keyHolder);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        final PostCreateResponse response = new PostCreateResponse();
        response.id = keyHolder.getKey().intValue();
        response.user = request.user;
        response.message = request.message;
        response.forum = request.forum;
        response.thread = request.thread;
        response.parent = request.parent;
        response.date = request.date;
        response.isApproved = request.isApproved;
        response.isHighlighted = request.isHighlighted;
        response.isEdited = request.isEdited;
        response.isSpam = request.isSpam;
        response.isDeleted = request.isDeleted;
        return ResponseEntity.ok(ResponseBody.ok(response));
    }

    @RequestMapping(path = "db/api/thread/subscribe", method = RequestMethod.POST)
    public ResponseEntity subscribe(@RequestBody Subscription subscription) {
        if (subscription.thread == null || StringUtils.isEmpty(subscription.user)) {
            return ResponseEntity.ok(ResponseBody.invalid());
        }
        try {
            jdbcTemplate.update("INSERT INTO subscription (user_email, thread_id) VALUES (?, ?);", subscription.user,
                    subscription.thread);
        } catch (DuplicateKeyException ignore) {
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        return ResponseEntity.ok(ResponseBody.ok(subscription));
    }

    @RequestMapping(path = "db/api/thread/unsubscribe", method = RequestMethod.POST)
    public ResponseEntity unsubscribe(@RequestBody Subscription subscription) {
        if (subscription.thread == null || StringUtils.isEmpty(subscription.user)) {
            return ResponseEntity.ok(ResponseBody.invalid());
        }
        jdbcTemplate.update("DELETE FROM subscription WHERE user_email = ? AND thread_id = ?;", subscription.user,
                subscription.thread);
        return ResponseEntity.ok(ResponseBody.ok(subscription));
    }

    @Transactional
    @RequestMapping(path = "db/api/user/follow", method = RequestMethod.POST)
    public ResponseEntity follow(@RequestBody Following following) {
        if (StringUtils.isEmpty(following.follower) || StringUtils.isEmpty(following.followee)) {
            return ResponseEntity.ok(ResponseBody.invalid());
        }
        try {
            jdbcTemplate.update("INSERT INTO following (follower, followee) VALUES (?, ?);", following.follower,
                    following.followee);
        } catch (DuplicateKeyException ignore) {
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        return ResponseEntity.ok(ResponseBody.ok(UserDetails.get(following.follower)));
    }

    @Transactional
    @RequestMapping(path = "db/api/user/unfollow", method = RequestMethod.POST)
    public ResponseEntity unfollow(@RequestBody Following following) {
        if (StringUtils.isEmpty(following.follower) || StringUtils.isEmpty(following.followee)) {
            return ResponseEntity.ok(ResponseBody.invalid());
        }
        jdbcTemplate.update("DELETE FROM following WHERE follower = ? AND followee = ?;", following.follower,
                following.followee);
        final UserDetails details = UserDetails.get(following.follower);
        if (details == null) {
            return ResponseEntity.ok(ResponseBody.notFound());
        }
        return ResponseEntity.ok(ResponseBody.ok(details));
    }

    @Transactional
    @RequestMapping(path = "db/api/user/updateProfile", method = RequestMethod.POST)
    public ResponseEntity updateUser(@RequestBody UserUpdateRequest request) {
        if (StringUtils.isEmpty(request.about) || StringUtils.isEmpty(request.user) ||
                StringUtils.isEmpty(request.name)) {
            return ResponseEntity.ok(ResponseBody.invalid());
        }
        jdbcTemplate.update("UPDATE user_profile SET about = ?, name = ? WHERE email = ?;", request.about, request.name,
                request.user);
        final UserDetails details = UserDetails.get(request.user);
        if (details == null) {
            return ResponseEntity.ok(ResponseBody.notFound());
        }
        return ResponseEntity.ok(ResponseBody.ok(details));
    }

    @Transactional
    @RequestMapping(path = "db/api/user/details", method = RequestMethod.GET)
    public ResponseEntity userDetails(@RequestParam(name = "user") String email) {
        if (StringUtils.isEmpty(email)) {
            return ResponseEntity.ok(ResponseBody.notFound());
        }
        final UserDetails details = UserDetails.get(email);
        if (details == null) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        return ResponseEntity.ok(ResponseBody.ok(details));
    }

    @Transactional
    @RequestMapping(path = "db/api/forum/details", method = RequestMethod.GET)
    public ResponseEntity forumDetails(@RequestParam(name = "forum") String forum,
                                       @RequestParam(name = "related", required = false) String[] related) {
        if (StringUtils.isEmpty(forum)) {
            return ResponseEntity.ok(ResponseBody.invalid());
        }
        if (!Utils.isArrayValid(related, "user")) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        final ForumDetails details = ForumDetails.get(forum, related);
        if (details == null) {
            return ResponseEntity.ok(ResponseBody.notFound());
        }
        return ResponseEntity.ok(ResponseBody.ok(details));
    }

    @Transactional
    @RequestMapping(path = "db/api/thread/details", method = RequestMethod.GET)
    public ResponseEntity threadDetails(@RequestParam(name = "thread") int thread,
                                        @RequestParam(name = "related", required = false) String[] related) {
        if (!Utils.isArrayValid(related, "user", "forum")) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        final ThreadDetails details = ThreadDetails.get(thread, related);
        if (details == null) {
            return ResponseEntity.ok(ResponseBody.notFound());
        }
        return ResponseEntity.ok(ResponseBody.ok(details));
    }

    @Transactional
    @RequestMapping(path = "db/api/post/details", method = RequestMethod.GET)
    public ResponseEntity postDetails(@RequestParam(name = "post") int post,
                                      @RequestParam(name = "related", required = false) String[] related) {
        if (!Utils.isArrayValid(related, "user", "thread", "forum")) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        final PostDetails details = PostDetails.get(post, related);
        if (details == null) {
            return ResponseEntity.ok(ResponseBody.notFound());
        }
        return ResponseEntity.ok(ResponseBody.ok(details));
    }

    @Transactional
    @RequestMapping(path = "db/api/user/listFollowers", method = RequestMethod.GET)
    public ResponseEntity listFollowers(@RequestParam(name = "user") String user,
                                        @RequestParam(name = "limit", required = false) Integer limit,
                                        @RequestParam(name = "order", required = false) String order,
                                        @RequestParam(name = "since_id", required = false) Integer since) {
        if (StringUtils.isEmpty(user) || (limit != null && limit < 0)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        if (StringUtils.isEmpty(order)) {
            order = "desc";
        }
        if (since == null) {
            since = 0;
        }
        if (!"desc".equalsIgnoreCase(order) && !"asc".equalsIgnoreCase(order)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        final String limitQuery = limit == null ? "" : " LIMIT " + limit;
        final SqlRowSet set = jdbcTemplate.queryForRowSet("SELECT follower FROM following JOIN user_profile ON " +
                "following.follower = user_profile.email WHERE followee = ? AND id >= ? ORDER BY name " + order +
                limitQuery + ';', user, since);
        final List<UserDetails> followers = new ArrayList<>();
        while (set.next()) {
            followers.add(UserDetails.get(set.getString("follower")));
        }
        return ResponseEntity.ok(ResponseBody.ok(followers.toArray()));
    }

    @Transactional
    @RequestMapping(path = "db/api/user/listFollowing", method = RequestMethod.GET)
    public ResponseEntity listFollowing(@RequestParam(name = "user") String user,
                                        @RequestParam(name = "limit", required = false) Integer limit,
                                        @RequestParam(name = "order", required = false) String order,
                                        @RequestParam(name = "since_id", required = false) Integer since) {
        if (StringUtils.isEmpty(user) || (limit != null && limit < 0)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        if (StringUtils.isEmpty(order)) {
            order = "desc";
        }
        if (since == null) {
            since = 0;
        }
        if (!"desc".equalsIgnoreCase(order) && !"asc".equalsIgnoreCase(order)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        final String limitQuery = limit == null ? "" : " LIMIT " + limit;
        final SqlRowSet set = jdbcTemplate.queryForRowSet("SELECT followee FROM following JOIN user_profile ON " +
                "following.followee = user_profile.email WHERE follower = ? AND id >= ? ORDER BY name " + order +
                limitQuery + ';', user, since);
        final List<UserDetails> followees = new ArrayList<>();
        while (set.next()) {
            followees.add(UserDetails.get(set.getString("followee")));
        }
        return ResponseEntity.ok(ResponseBody.ok(followees.toArray()));
    }

    @Transactional
    @RequestMapping(path = "db/api/user/listPosts", method = RequestMethod.GET)
    public ResponseEntity listUserPosts(@RequestParam(name = "user") String user,
                                        @RequestParam(name = "limit", required = false) Integer limit,
                                        @RequestParam(name = "order", required = false) String order,
                                        @RequestParam(name = "since", required = false) String since) {
        if (StringUtils.isEmpty(user) || (limit != null && limit < 0)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        if (StringUtils.isEmpty(order)) {
            order = "desc";
        }
        if (!"desc".equalsIgnoreCase(order) && !"asc".equalsIgnoreCase(order)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        since = Utils.validSince(since);
        if (since == null) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        final String limitQuery = limit == null ? "" : " LIMIT " + limit;
        final SqlRowSet set = jdbcTemplate.queryForRowSet("SELECT * FROM post WHERE user_email = ? AND " +
                "creation_time >= ? ORDER BY creation_time " + order + limitQuery + ';', user, since);
        final List<PostDetails> posts = new ArrayList<>();
        while (set.next()) {
            posts.add(new PostDetails(set));
        }
        return ResponseEntity.ok(ResponseBody.ok(posts.toArray()));
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Transactional
    @RequestMapping(path = "db/api/forum/listUsers", method = RequestMethod.GET)
    public ResponseEntity listForumUsers(@RequestParam(name = "forum") String forum,
                                         @RequestParam(name = "limit", required = false) Integer limit,
                                         @RequestParam(name = "order", required = false) String order,
                                         @RequestParam(name = "since_id", required = false) Integer since) {
        final boolean isSinceNotNull = since != null;
        final boolean isLimitNotNull = limit != null;
        if (StringUtils.isEmpty(forum) || (isLimitNotNull && limit < 0)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        if (StringUtils.isEmpty(order)) {
            order = "desc";
        }
        if (!"desc".equalsIgnoreCase(order) && !"asc".equalsIgnoreCase(order)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        String query = "SELECT * FROM user_profile WHERE email IN (SELECT DISTINCT user_email FROM post WHERE " +
                "forum = ?) ";
        if (isSinceNotNull) {
            query += "AND id >= ? ";
        }
        query += "ORDER BY name " + order;
        if (isLimitNotNull) {
            query += " LIMIT ?";
        }
        query += ';';
        final SqlRowSet set;
        if (isSinceNotNull && isLimitNotNull) {
            set = jdbcTemplate.queryForRowSet(query, forum, since, limit);
        } else if (isLimitNotNull) {
            set = jdbcTemplate.queryForRowSet(query, forum, limit);
        } else if (isSinceNotNull) {
            set = jdbcTemplate.queryForRowSet(query, forum, since);
        } else {
            set = jdbcTemplate.queryForRowSet(query, forum);
        }
        final List<UserDetails> list = new ArrayList<>();
        while (set.next()) {
            final UserDetails details = new UserDetails();
            details.about = set.getString("about");
            details.email = set.getString("email");
            details.id = set.getInt("id");
            details.isAnonymous = set.getBoolean("isAnonymous");
            details.username = set.getString("username");
            details.name = set.getString("name");
            final SqlRowSet followerSet = jdbcTemplate.queryForRowSet("SELECT follower FROM following WHERE " +
                    "followee = ?;", details.email);
            final List<String> followerList = new ArrayList<>();
            while (followerSet.next()) {
                followerList.add(followerSet.getString("follower"));
            }
            details.followers = followerList.toArray(new String[followerList.size()]);
            final SqlRowSet followeeSet = jdbcTemplate.queryForRowSet("SELECT followee FROM following WHERE " +
                    "follower = ?;", details.email);
            final List<String> followeeList = new ArrayList<>();
            while (followeeSet.next()) {
                followeeList.add(followeeSet.getString("followee"));
            }
            details.following = followeeList.toArray(new String[followeeList.size()]);
            final SqlRowSet subscriptionSet = jdbcTemplate.queryForRowSet("SELECT thread_id FROM subscription WHERE " +
                    "user_email = ?;", details.email);
            final List<Integer> subscriptionList = new ArrayList<>();
            while (subscriptionSet.next()) {
                subscriptionList.add(subscriptionSet.getInt("thread_id"));
            }
            details.subscriptions = subscriptionList.stream().mapToInt(i -> i).toArray();
            list.add(details);
        }
        return ResponseEntity.ok(ResponseBody.ok(list.toArray()));
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Transactional
    @RequestMapping(path = "db/api/forum/listThreads", method = RequestMethod.GET)
    public ResponseEntity listForumThreads(@RequestParam(name = "forum") String forum,
                                           @RequestParam(name = "limit", required = false) Integer limit,
                                           @RequestParam(name = "order", required = false) String order,
                                           @RequestParam(name = "since", required = false) String since,
                                           @RequestParam(name = "related", required = false) String[] related) {
        if (StringUtils.isEmpty(forum) || (limit != null && limit < 0)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        if (!Utils.isArrayValid(related, "user", "forum")) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        if (StringUtils.isEmpty(order)) {
            order = "desc";
        }
        if (!"desc".equalsIgnoreCase(order) && !"asc".equalsIgnoreCase(order)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        since = Utils.validSince(since);
        if (since == null) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        final String limitQuery = limit == null ? "" : " LIMIT " + limit;
        final SqlRowSet set = jdbcTemplate.queryForRowSet("SELECT creation_time, dislikes, id, isClosed, " +
                "isDeleted, likes, message, slug, title, forum, user_email, posts FROM thread WHERE forum = ? AND " +
                "creation_time >= ? ORDER BY creation_time " + order + limitQuery + ';', forum, since);
        final List<ThreadDetails> list = new ArrayList<>();
        while (set.next()) {
            list.add(new ThreadDetails(set, related));
        }
        return ResponseEntity.ok(ResponseBody.ok(list.toArray()));
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Transactional
    @RequestMapping(path = "db/api/forum/listPosts", method = RequestMethod.GET)
    public ResponseEntity listForumPosts(@RequestParam(name = "forum") String forum,
                                         @RequestParam(name = "limit", required = false) Integer limit,
                                         @RequestParam(name = "order", required = false) String order,
                                         @RequestParam(name = "since", required = false) String since,
                                         @RequestParam(name = "related", required = false) String[] related) {
        if (StringUtils.isEmpty(forum) || (limit != null && limit < 0)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        if (!Utils.isArrayValid(related, "user", "forum", "thread")) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        if (StringUtils.isEmpty(order)) {
            order = "desc";
        }
        if (!"desc".equalsIgnoreCase(order) && !"asc".equalsIgnoreCase(order)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        since = Utils.validSince(since);
        if (since == null) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        final String limitQuery = limit == null ? "" : " LIMIT " + limit;
        final SqlRowSet set = jdbcTemplate.queryForRowSet("SELECT creation_time, dislikes, id, isApproved, " +
                "isDeleted, isEdited, isHighlighted, isSpam, likes, message, parent, forum, thread_id, " +
                "user_email FROM post WHERE forum = ? AND creation_time >= ? ORDER BY creation_time " + order +
                limitQuery + ';', forum, since);
        final List<PostDetails> list = new ArrayList<>();
        while (set.next()) {
            list.add(new PostDetails(set, related));
        }
        return ResponseEntity.ok(ResponseBody.ok(list.toArray()));
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Transactional
    @RequestMapping(path = "db/api/post/list", method = RequestMethod.GET)
    public ResponseEntity listPosts(@RequestParam(name = "forum", required = false) String forum,
                                    @RequestParam(name = "thread", required = false) Integer thread,
                                    @RequestParam(name = "limit", required = false) Integer limit,
                                    @RequestParam(name = "order", required = false) String order,
                                    @RequestParam(name = "since", required = false) String since) {
        if (StringUtils.isEmpty(forum) == (thread == null)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        if (limit != null && limit < 0) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        if (StringUtils.isEmpty(order)) {
            order = "desc";
        }
        if (!"desc".equalsIgnoreCase(order) && !"asc".equalsIgnoreCase(order)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        since = Utils.validSince(since);
        if (since == null) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        final String limitQuery = limit == null ? "" : " LIMIT " + limit;
        final SqlRowSet set;
        if (StringUtils.isEmpty(forum)) {
            set = jdbcTemplate.queryForRowSet("SELECT creation_time, dislikes, id, isApproved, " +
                    "isDeleted, isEdited, isHighlighted, isSpam, likes, message, parent, forum, " +
                    "thread_id, user_email FROM post WHERE thread_id = ? AND creation_time >= ? " +
                    "ORDER BY creation_time " + order + limitQuery + ';', thread, since);
        } else {
            set = jdbcTemplate.queryForRowSet("SELECT creation_time, dislikes, id, isApproved, isDeleted, " +
                    "isEdited, isHighlighted, isSpam, likes, message, parent, forum, thread_id, user_email " +
                    "FROM post WHERE forum = ? AND creation_time >= ? ORDER BY creation_time " + order + limitQuery +
                    ';', forum, since);
        }
        final List<PostDetails> posts = new ArrayList<>();
        while (set.next()) {
            posts.add(new PostDetails(set));
        }
        return ResponseEntity.ok(ResponseBody.ok(posts.toArray()));
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Transactional
    @RequestMapping(path = "db/api/thread/list", method = RequestMethod.GET)
    public ResponseEntity listThreads(@RequestParam(name = "forum", required = false) String forum,
                                      @RequestParam(name = "user", required = false) String user,
                                      @RequestParam(name = "limit", required = false) Integer limit,
                                      @RequestParam(name = "order", required = false) String order,
                                      @RequestParam(name = "since", required = false) String since) {
        if (StringUtils.isEmpty(forum) == StringUtils.isEmpty(user)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        if (limit != null && limit < 0) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        if (StringUtils.isEmpty(order)) {
            order = "desc";
        }
        if (!"desc".equalsIgnoreCase(order) && !"asc".equalsIgnoreCase(order)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        since = Utils.validSince(since);
        if (since == null) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        final String limitQuery = limit == null ? "" : " LIMIT " + limit;
        final SqlRowSet set;
        if (StringUtils.isEmpty(forum)) {
            set = jdbcTemplate.queryForRowSet("SELECT creation_time, dislikes, id, isClosed, isDeleted, likes, " +
                    "message, slug, title, forum, user_email, posts FROM thread WHERE user_email = ? AND " +
                    "creation_time >= ? ORDER BY creation_time " + order + limitQuery + ';', user, since);
        } else {
            set = jdbcTemplate.queryForRowSet("SELECT creation_time, dislikes, id, isClosed, isDeleted, likes, " +
                    "message, slug, title, forum, user_email, posts FROM thread WHERE forum = ? " +
                    "AND creation_time >= ? ORDER BY creation_time " + order + limitQuery + ';', forum, since);
        }
        final List<ThreadDetails> list = new ArrayList<>();
        while (set.next()) {
            list.add(new ThreadDetails(set));
        }
        return ResponseEntity.ok(ResponseBody.ok(list.toArray()));
    }

    @Transactional
    @RequestMapping(path = "db/api/post/update", method = RequestMethod.POST)
    public ResponseEntity updatePost(@RequestBody PostUpdateRequest request) {
        if (StringUtils.isEmpty(request.message)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        jdbcTemplate.update("UPDATE post SET message = ? WHERE id = ?;", request.message, request.post);
        final PostDetails details = PostDetails.get(request.post, null);
        if (details == null) {
            return ResponseEntity.ok(ResponseBody.notFound());
        }
        return ResponseEntity.ok(ResponseBody.ok(details));

    }

    @RequestMapping(path = "db/api/post/remove", method = RequestMethod.POST)
    public ResponseEntity deletePost(@RequestBody PostID request) {
        jdbcTemplate.update("UPDATE post SET isDeleted = TRUE WHERE id = ?;", request.post);
        jdbcTemplate.update("UPDATE thread SET posts = posts - 1 WHERE id = (SELECT thread_id FROM post WHERE id = ?);",
                request.post);
        return ResponseEntity.ok(ResponseBody.ok(request));
    }

    @RequestMapping(path = "db/api/post/restore", method = RequestMethod.POST)
    public ResponseEntity restorePost(@RequestBody PostID request) {
        jdbcTemplate.update("UPDATE post SET isDeleted = FALSE WHERE id = ?;", request.post);
        jdbcTemplate.update("UPDATE thread SET posts = posts + 1 WHERE id = (SELECT thread_id FROM post WHERE id = ?);",
                request.post);
        return ResponseEntity.ok(ResponseBody.ok(request));
    }

    @Transactional
    @RequestMapping(path = "db/api/post/vote", method = RequestMethod.POST)
    public ResponseEntity ratePost(@RequestBody PostVote request) {
        final String field = Utils.getFieldVote(request.vote);
        if (field == null) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        jdbcTemplate.update(String.format("UPDATE post SET %s = %s + 1 WHERE id = ?;", field, field), request.post);
        final PostDetails details = PostDetails.get(request.post, null);
        if (details == null) {
            return ResponseEntity.ok(ResponseBody.notFound());
        }
        return ResponseEntity.ok(ResponseBody.ok(details));
    }

    @Transactional
    @RequestMapping(path = "db/api/thread/vote", method = RequestMethod.POST)
    public ResponseEntity rateThread(@RequestBody ThreadVote request) {
        final String vote = Utils.getFieldVote(request.vote);
        if (vote == null) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        jdbcTemplate.update(String.format("UPDATE thread SET %s = %s + 1 WHERE id = ?;", vote, vote), request.thread);
        final ThreadDetails details = ThreadDetails.get(request.thread, null);
        if (details == null) {
            return ResponseEntity.ok(ResponseBody.notFound());
        }
        return ResponseEntity.ok(ResponseBody.ok(details));
    }

    @RequestMapping(path = "db/api/thread/close", method = RequestMethod.POST)
    public ResponseEntity closeThread(@RequestBody ThreadID request) {
        jdbcTemplate.update("UPDATE thread SET isClosed = TRUE WHERE id = ?;", request.thread);
        return ResponseEntity.ok(ResponseBody.ok(request));
    }

    @RequestMapping(path = "db/api/thread/open", method = RequestMethod.POST)
    public ResponseEntity openThread(@RequestBody ThreadID request) {
        jdbcTemplate.update("UPDATE thread SET isClosed = FALSE WHERE id = ?;", request.thread);
        return ResponseEntity.ok(ResponseBody.ok(request));
    }

    @Transactional
    @RequestMapping(path = "db/api/thread/remove", method = RequestMethod.POST)
    public ResponseEntity deleteThread(@RequestBody ThreadID request) {
        jdbcTemplate.update("UPDATE thread SET isDeleted = TRUE, posts = 0 WHERE id = ?;", request.thread);
        jdbcTemplate.update("UPDATE post SET isDeleted = TRUE WHERE thread_id = ?;", request.thread);
        return ResponseEntity.ok(ResponseBody.ok(request));
    }

    @Transactional
    @RequestMapping(path = "db/api/thread/restore", method = RequestMethod.POST)
    public ResponseEntity restoreThread(@RequestBody ThreadID request) {
        jdbcTemplate.update("UPDATE thread SET isDeleted = FALSE, posts = (" +
                "SELECT count(*) FROM post WHERE thread_id = ?) WHERE id = ?;", request.thread, request.thread);
        jdbcTemplate.update("UPDATE post SET isDeleted = FALSE WHERE thread_id = ?;", request.thread);
        return ResponseEntity.ok(ResponseBody.ok(request));
    }

    @Transactional
    @RequestMapping(path = "db/api/thread/update", method = RequestMethod.POST)
    public ResponseEntity updatePost(@RequestBody ThreadUpdateRequest request) {
        if (StringUtils.isEmpty(request.message) || StringUtils.isEmpty(request.slug)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        jdbcTemplate.update("UPDATE thread SET message = ?, slug = ? WHERE id = ?;", request.message, request.slug,
                request.thread);
        final ThreadDetails details = ThreadDetails.get(request.thread, null);
        if (details == null) {
            return ResponseEntity.ok(ResponseBody.notFound());
        }
        return ResponseEntity.ok(ResponseBody.ok(details));
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Transactional
    @RequestMapping(path = "db/api/thread/listPosts", method = RequestMethod.GET)
    public ResponseEntity listThreadPosts(@RequestParam(name = "thread") int thread,
                                          @RequestParam(name = "limit", required = false) Integer limit,
                                          @RequestParam(name = "sort", required = false) String sort,
                                          @RequestParam(name = "order", required = false) String order,
                                          @RequestParam(name = "since", required = false) String since) {
        if (limit != null && limit < 0) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        if (StringUtils.isEmpty(sort)) {
            sort = "flat";
        }
        if (!"flat".equalsIgnoreCase(sort) && !"tree".equalsIgnoreCase(sort) && !"parent_tree".equalsIgnoreCase(sort)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        if (StringUtils.isEmpty(order)) {
            order = "desc";
        }
        if (!"desc".equalsIgnoreCase(order) && !"asc".equalsIgnoreCase(order)) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        since = Utils.validSince(since);
        if (since == null) {
            return ResponseEntity.ok(ResponseBody.incorrect());
        }
        final SqlRowSet set = jdbcTemplate.queryForRowSet("SELECT creation_time, dislikes, id, isApproved, " +
                "isDeleted, isEdited, isHighlighted, isSpam, likes, message, parent, forum, thread_id, user_email " +
                "FROM post WHERE thread_id = ? AND creation_time >= ? ORDER BY creation_time " + order + ';',
                thread, since);
        final List<PostDetails> list = new ArrayList<>();
        while (set.next()) {
            list.add(new PostDetails(set));
        }
        return ResponseEntity.ok(ResponseBody.ok(PostDetails.sortPosts(list, sort, limit,
                "desc".equalsIgnoreCase(order)).toArray()));
    }

    @SuppressWarnings("InstanceMethodNamingConvention")
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity handleHttpMessageNotReadableException() {
        return ResponseEntity.ok(ResponseBody.invalid());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity handleDataAccessException() {
        return ResponseEntity.ok(ResponseBody.unknownError());
    }

    @SuppressWarnings("unused")
    private static final class ResponseBody {
        private int code;
        private Object response;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public ResponseBody() {
        }

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public ResponseBody(int code, Object response) {
            this.code = code;
            this.response = response;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public Object getResponse() {
            return response;
        }

        public void setResponse(Object response) {
            this.response = response;
        }

        @SuppressWarnings("StaticMethodNamingConvention")
        public static ResponseBody ok() {
            return new ResponseBody(0, "OK");
        }

        @SuppressWarnings("StaticMethodNamingConvention")
        public static ResponseBody ok(Object response) {
            return new ResponseBody(0, response);
        }

        public static ResponseBody notFound() {
            return new ResponseBody(1, "Requested object not found");
        }

        public static ResponseBody invalid() {
            return new ResponseBody(2, "Invalid request");
        }

        public static ResponseBody incorrect() {
            return new ResponseBody(3, "Incorrect request");
        }

        public static ResponseBody unknownError() {
            return new ResponseBody(4, "Unknown error");
        }

        public static ResponseBody userAlreadyExists() {
            return new ResponseBody(5, "User already exists");
        }
    }

    @SuppressWarnings("unused")
    private static final class StatusResponse {
        private int user;
        private int thread;
        private int forum;
        private int post;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public StatusResponse() {
        }

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public StatusResponse(int user, int thread, int forum, int post) {
            this.user = user;
            this.thread = thread;
            this.forum = forum;
            this.post = post;
        }

        public int getUser() {
            return user;
        }

        public void setUser(int user) {
            this.user = user;
        }

        public int getThread() {
            return thread;
        }

        public void setThread(int thread) {
            this.thread = thread;
        }

        public int getForum() {
            return forum;
        }

        public void setForum(int forum) {
            this.forum = forum;
        }

        public int getPost() {
            return post;
        }

        public void setPost(int post) {
            this.post = post;
        }
    }

    @SuppressWarnings("unused")
    private static final class UserCreateRequest {
        private String username;
        private String email;
        private String name;
        private String about;
        private boolean isAnonymous;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public UserCreateRequest() {
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAbout() {
            return about;
        }

        public void setAbout(String about) {
            this.about = about;
        }

        public boolean getIsAnonymous() {
            return isAnonymous;
        }

        public void setIsAnonymous(boolean isAnonymous) {
            this.isAnonymous = isAnonymous;
        }
    }

    @SuppressWarnings("unused")
    private static final class UserCreateResponse {
        private String about;
        private String email;
        private int id;
        private boolean isAnonymous;
        private String name;
        private String username;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public UserCreateResponse() {
        }

        public String getAbout() {
            return about;
        }

        public void setAbout(String about) {
            this.about = about;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public boolean getIsAnonymous() {
            return isAnonymous;
        }

        public void setIsAnonymous(boolean isAnonymous) {
            this.isAnonymous = isAnonymous;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }

    @SuppressWarnings("unused")
    private static final class ForumCreateRequest {
        private String name;
        @SuppressWarnings("InstanceVariableNamingConvention")
        private String short_name;
        private String user;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public ForumCreateRequest() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @SuppressWarnings("InstanceMethodNamingConvention")
        public String getShort_name() {
            return short_name;
        }

        @SuppressWarnings("InstanceMethodNamingConvention")
        public void setShort_name(String shortName) {
            short_name = shortName;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }
    }

    @SuppressWarnings("unused")
    private static final class ThreadCreateRequest {
        private String date;
        private String forum;
        private Boolean isClosed;
        private boolean isDeleted;
        private String message;
        private String slug;
        private String title;
        private String user;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public ThreadCreateRequest() {
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getForum() {
            return forum;
        }

        public void setForum(String forum) {
            this.forum = forum;
        }

        public boolean getIsClosed() {
            return isClosed;
        }

        public void setIsClosed(boolean closed) {
            isClosed = closed;
        }

        public boolean getIsDeleted() {
            return isDeleted;
        }

        public void setIsDeleted(boolean deleted) {
            isDeleted = deleted;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }
    }

    @SuppressWarnings("unused")
    private static final class ThreadCreateResponse {
        private String date;
        private String forum;
        private int id;
        private boolean isClosed;
        private boolean isDeleted;
        private String message;
        private String slug;
        private String title;
        private String user;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public ThreadCreateResponse() {
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getForum() {
            return forum;
        }

        public void setForum(String forum) {
            this.forum = forum;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public boolean getIsClosed() {
            return isClosed;
        }

        public void setIsClosed(boolean closed) {
            isClosed = closed;
        }

        public boolean getIsDeleted() {
            return isDeleted;
        }

        public void setIsDeleted(boolean deleted) {
            isDeleted = deleted;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }
    }

    @SuppressWarnings("unused")
    private static final class PostCreateRequest {
        private String date;
        private String forum;
        private boolean isApproved;
        private boolean isDeleted;
        private boolean isEdited;
        private boolean isHighlighted;
        private boolean isSpam;
        private String message;
        private Integer parent;
        private Integer thread;
        private String user;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public PostCreateRequest() {
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getForum() {
            return forum;
        }

        public void setForum(String forum) {
            this.forum = forum;
        }

        public boolean getIsApproved() {
            return isApproved;
        }

        public void setIsApproved(boolean approved) {
            isApproved = approved;
        }

        public boolean getIsDeleted() {
            return isDeleted;
        }

        public void setIsDeleted(boolean deleted) {
            isDeleted = deleted;
        }

        public boolean getIsEdited() {
            return isEdited;
        }

        public void setIsEdited(boolean edited) {
            isEdited = edited;
        }

        public boolean getIsHighlighted() {
            return isHighlighted;
        }

        public void setIsHighlighted(boolean highlighted) {
            isHighlighted = highlighted;
        }

        public boolean getIsSpam() {
            return isSpam;
        }

        public void setIsSpam(boolean spam) {
            isSpam = spam;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Integer getParent() {
            return parent;
        }

        public void setParent(Integer parent) {
            this.parent = parent;
        }

        public Integer getThread() {
            return thread;
        }

        public void setThread(Integer thread) {
            this.thread = thread;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }
    }

    @SuppressWarnings("unused")
    private static final class PostCreateResponse {
        private String date;
        private String forum;
        private int id;
        private boolean isApproved;
        private boolean isDeleted;
        private boolean isEdited;
        private boolean isHighlighted;
        private boolean isSpam;
        private String message;
        private Integer parent;
        private Integer thread;
        private String user;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public PostCreateResponse() {
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getForum() {
            return forum;
        }

        public void setForum(String forum) {
            this.forum = forum;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public boolean getIsApproved() {
            return isApproved;
        }

        public void setIsApproved(boolean approved) {
            isApproved = approved;
        }

        public boolean getIsDeleted() {
            return isDeleted;
        }

        public void setIsDeleted(boolean deleted) {
            isDeleted = deleted;
        }

        public boolean getIsEdited() {
            return isEdited;
        }

        public void setIsEdited(boolean edited) {
            isEdited = edited;
        }

        public boolean getIsHighlighted() {
            return isHighlighted;
        }

        public void setIsHighlighted(boolean highlighted) {
            isHighlighted = highlighted;
        }

        public boolean getIsSpam() {
            return isSpam;
        }

        public void setIsSpam(boolean spam) {
            isSpam = spam;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Integer getParent() {
            return parent;
        }

        public void setParent(Integer parent) {
            this.parent = parent;
        }

        public Integer getThread() {
            return thread;
        }

        public void setThread(Integer thread) {
            this.thread = thread;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }
    }

    @SuppressWarnings("unused")
    private static final class Subscription {
        private Integer thread;
        private String user;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public Subscription() {
        }

        public Integer getThread() {
            return thread;
        }

        public void setThread(Integer thread) {
            this.thread = thread;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }
    }

    @SuppressWarnings("unused")
    private static final class Following {
        private String follower;
        private String followee;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public Following() {
        }

        public String getFollower() {
            return follower;
        }

        public void setFollower(String follower) {
            this.follower = follower;
        }

        public String getFollowee() {
            return followee;
        }

        public void setFollowee(String followee) {
            this.followee = followee;
        }
    }

    @SuppressWarnings("unused")
    private static final class UserDetails {
        private String about;
        private String email;
        private String[] followers;
        private String[] following;
        private int id;
        private boolean isAnonymous;
        private String name;
        private int[] subscriptions;
        private String username;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public UserDetails() {
        }

        public String getAbout() {
            return about;
        }

        public void setAbout(String about) {
            this.about = about;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String[] getFollowers() {
            return followers;
        }

        public void setFollowers(String[] followers) {
            this.followers = followers;
        }

        public String[] getFollowing() {
            return following;
        }

        public void setFollowing(String[] following) {
            this.following = following;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public boolean getIsAnonymous() {
            return isAnonymous;
        }

        public void setIsAnonymous(boolean anonymous) {
            isAnonymous = anonymous;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int[] getSubscriptions() {
            return subscriptions;
        }

        public void setSubscriptions(int[] subscriptions) {
            this.subscriptions = subscriptions;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        @SuppressWarnings("StaticMethodNamingConvention")
        public static UserDetails get(String email) {
            final SqlRowSet user = jdbcTemplate.queryForRowSet("SELECT * FROM user_profile WHERE email = ?;", email);
            if (!user.next()) {
                return null;
            }
            final UserDetails details = new UserDetails();
            details.about = user.getString("about");
            details.email = user.getString("email");
            details.id = user.getInt("id");
            details.isAnonymous = user.getBoolean("isAnonymous");
            details.username = user.getString("username");
            details.name = user.getString("name");
            final SqlRowSet followerSet = jdbcTemplate.queryForRowSet("SELECT follower FROM following WHERE " +
                    "followee = ?;", email);
            final List<String> followerList = new ArrayList<>();
            while (followerSet.next()) {
                followerList.add(followerSet.getString("follower"));
            }
            details.followers = followerList.toArray(new String[followerList.size()]);
            final SqlRowSet followeeSet = jdbcTemplate.queryForRowSet("SELECT followee FROM following WHERE " +
                    "follower = ?;", email);
            final List<String> followeeList = new ArrayList<>();
            while (followeeSet.next()) {
                followeeList.add(followeeSet.getString("followee"));
            }
            details.following = followeeList.toArray(new String[followeeList.size()]);
            final SqlRowSet subscriptionSet = jdbcTemplate.queryForRowSet("SELECT thread_id FROM subscription WHERE " +
                    "user_email = ?;", email);
            final List<Integer> subscriptionList = new ArrayList<>();
            while (subscriptionSet.next()) {
                subscriptionList.add(subscriptionSet.getInt("thread_id"));
            }
            details.subscriptions = subscriptionList.stream().mapToInt(i -> i).toArray();
            return details;
        }
    }

    @SuppressWarnings("unused")
    private static final class ForumDetails {
        private int id;
        private String name;
        @SuppressWarnings("InstanceVariableNamingConvention")
        private String short_name;
        private Object user;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public ForumDetails() {
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @SuppressWarnings("InstanceMethodNamingConvention")
        public String getShort_name() {
            return short_name;
        }

        @SuppressWarnings("InstanceMethodNamingConvention")
        public void setShort_name(String shortName) {
            short_name = shortName;
        }

        public Object getUser() {
            return user;
        }

        public void setUser(Object user) {
            this.user = user;
        }

        @SuppressWarnings("StaticMethodNamingConvention")
        public static ForumDetails get(String shortName, String[] related) {
            final SqlRowSet forum = jdbcTemplate.queryForRowSet("SELECT * FROM forum WHERE short_name = ?;", shortName);
            if (!forum.next()) {
                return null;
            }
            final ForumDetails details = new ForumDetails();
            details.id = forum.getInt("id");
            details.name = forum.getString("name");
            details.short_name = shortName;
            if (related != null && Arrays.asList(related).contains("user")) {
                details.user = UserDetails.get(forum.getString("user_email"));
            } else {
                details.user = forum.getString("user_email");
            }
            return details;
        }
    }

    @SuppressWarnings("unused")
    private static final class ThreadDetails {
        private String date;
        private int dislikes;
        private Object forum;
        private int id;
        private boolean isClosed;
        private boolean isDeleted;
        private int likes;
        private String message;
        private int points;
        private int posts;
        private String slug;
        private String title;
        private Object user;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public ThreadDetails() {
        }

        private ThreadDetails(SqlRowSet set) {
            date = Utils.DATE_FORMAT.format(set.getTimestamp("creation_time"));
            dislikes = set.getInt("dislikes");
            id = set.getInt("id");
            isClosed = set.getBoolean("isClosed");
            isDeleted = set.getBoolean("isDeleted");
            likes = set.getInt("likes");
            message = set.getString("message");
            points = likes - dislikes;
            posts = set.getInt("posts");
            slug = set.getString("slug");
            title = set.getString("title");
            forum = set.getString("forum");
            user = set.getString("user_email");
        }

        private ThreadDetails(SqlRowSet set, String[] related) {
            date = Utils.DATE_FORMAT.format(set.getTimestamp("creation_time"));
            dislikes = set.getInt("dislikes");
            id = set.getInt("id");
            isClosed = set.getBoolean("isClosed");
            isDeleted = set.getBoolean("isDeleted");
            likes = set.getInt("likes");
            message = set.getString("message");
            points = likes - dislikes;
            posts = set.getInt("posts");
            slug = set.getString("slug");
            title = set.getString("title");
            processRelated(related, set);
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public int getDislikes() {
            return dislikes;
        }

        public void setDislikes(int dislikes) {
            this.dislikes = dislikes;
        }

        public Object getForum() {
            return forum;
        }

        public void setForum(Object forum) {
            this.forum = forum;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public boolean getIsClosed() {
            return isClosed;
        }

        public void setIsClosed(boolean closed) {
            isClosed = closed;
        }

        public boolean getIsDeleted() {
            return isDeleted;
        }

        public void setIsDeleted(boolean deleted) {
            isDeleted = deleted;
        }

        public int getLikes() {
            return likes;
        }

        public void setLikes(int likes) {
            this.likes = likes;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public int getPoints() {
            return points;
        }

        public void setPoints(int points) {
            this.points = points;
        }

        public int getPosts() {
            return posts;
        }

        public void setPosts(int posts) {
            this.posts = posts;
        }

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Object getUser() {
            return user;
        }

        public void setUser(Object user) {
            this.user = user;
        }

        @SuppressWarnings("StaticMethodNamingConvention")
        public static ThreadDetails get(int id, String[] related) {
            final SqlRowSet thread = jdbcTemplate.queryForRowSet("SELECT * FROM thread WHERE id = ?;", id);
            if (!thread.next()) {
                return null;
            }
            return new ThreadDetails(thread, related);
        }

        private void processRelated(String[] related, SqlRowSet set) {
            if (related != null) {
                final List relatedList = Arrays.asList(related);
                if (relatedList.contains("forum")) {
                    forum = ForumDetails.get(set.getString("forum"), null);
                } else {
                    forum = set.getString("forum");
                }
                if (relatedList.contains("user")) {
                    user = UserDetails.get(set.getString("user_email"));
                } else {
                    user = set.getString("user_email");
                }
            } else {
                forum = set.getString("forum");
                user = set.getString("user_email");
            }
        }
    }

    @SuppressWarnings("unused")
    private static final class PostDetails {
        private String date;
        private int dislikes;
        private Object forum;
        private int id;
        private boolean isApproved;
        private boolean isDeleted;
        private boolean isEdited;
        private boolean isHighlighted;
        private boolean isSpam;
        private int likes;
        private String message;
        private Integer parent;
        private int points;
        private Object thread;
        private Object user;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public PostDetails() {
        }

        private PostDetails(SqlRowSet set) {
            date = Utils.DATE_FORMAT.format(set.getTimestamp("creation_time"));
            dislikes = set.getInt("dislikes");
            id = set.getInt("id");
            isApproved = set.getBoolean("isApproved");
            isDeleted = set.getBoolean("isDeleted");
            isEdited = set.getBoolean("isEdited");
            isHighlighted = set.getBoolean("isHighlighted");
            isSpam = set.getBoolean("isSpam");
            likes = set.getInt("likes");
            message = set.getString("message");
            parent = (Integer) set.getObject("parent");
            points = likes - dislikes;
            forum = set.getString("forum");
            thread = set.getInt("thread_id");
            user = set.getString("user_email");
        }

        private PostDetails(SqlRowSet set, String[] related) {
            date = Utils.DATE_FORMAT.format(set.getTimestamp("creation_time"));
            dislikes = set.getInt("dislikes");
            id = set.getInt("id");
            isApproved = set.getBoolean("isApproved");
            isDeleted = set.getBoolean("isDeleted");
            isEdited = set.getBoolean("isEdited");
            isHighlighted = set.getBoolean("isHighlighted");
            isSpam = set.getBoolean("isSpam");
            likes = set.getInt("likes");
            message = set.getString("message");
            parent = (Integer) set.getObject("parent");
            points = likes - dislikes;
            forum = set.getString("forum");
            processRelated(related, set);
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public int getDislikes() {
            return dislikes;
        }

        public void setDislikes(int dislikes) {
            this.dislikes = dislikes;
        }

        public Object getForum() {
            return forum;
        }

        public void setForum(Object forum) {
            this.forum = forum;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public boolean getIsApproved() {
            return isApproved;
        }

        public void setIsApproved(boolean approved) {
            isApproved = approved;
        }

        public boolean getIsDeleted() {
            return isDeleted;
        }

        public void setIsDeleted(boolean deleted) {
            isDeleted = deleted;
        }

        public boolean getIsEdited() {
            return isEdited;
        }

        public void setIsEdited(boolean edited) {
            isEdited = edited;
        }

        public boolean getIsHighlighted() {
            return isHighlighted;
        }

        public void setIsHighlighted(boolean highlighted) {
            isHighlighted = highlighted;
        }

        public boolean getIsSpam() {
            return isSpam;
        }

        public void setIsSpam(boolean spam) {
            isSpam = spam;
        }

        public int getLikes() {
            return likes;
        }

        public void setLikes(int likes) {
            this.likes = likes;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Integer getParent() {
            return parent;
        }

        public void setParent(Integer parent) {
            this.parent = parent;
        }

        public int getPoints() {
            return points;
        }

        public void setPoints(int points) {
            this.points = points;
        }

        public Object getThread() {
            return thread;
        }

        public void setThread(Object thread) {
            this.thread = thread;
        }

        public Object getUser() {
            return user;
        }

        public void setUser(Object user) {
            this.user = user;
        }

        @SuppressWarnings("StaticMethodNamingConvention")
        public static PostDetails get(int id, String[] related) {
            final SqlRowSet post = jdbcTemplate.queryForRowSet("SELECT * FROM post WHERE id = ?;", id);
            if (!post.next()) {
                return null;
            }
            return new PostDetails(post, related);
        }

        private void processRelated(String[] related, SqlRowSet post) {
            if (related != null) {
                final List relatedList = Arrays.asList(related);
                if (relatedList.contains("forum")) {
                    forum = ForumDetails.get(post.getString("forum"), null);
                } else {
                    forum = post.getString("forum");
                }
                if (relatedList.contains("thread")) {
                    thread = ThreadDetails.get(post.getInt("thread_id"), null);
                } else {
                    thread = post.getInt("thread_id");
                }
                if (relatedList.contains("user")) {
                    user = UserDetails.get(post.getString("user_email"));
                } else {
                    user = post.getString("user_email");
                }
            } else {
                forum = post.getString("forum");
                thread = post.getInt("thread_id");
                user = post.getString("user_email");
            }
        }

        @SuppressWarnings("OverlyComplexMethod")
        public static List<PostDetails> sortPosts(List<PostDetails> posts, String sort, Integer limit, boolean desc) {
            if ("flat".equalsIgnoreCase(sort)) {
                if (limit == null || limit >= posts.size()) {
                    return posts;
                }
                return posts.subList(0, limit);
            }
            final List<PostDetails> list = PostTreeNode.list(PostTreeNode.tree(null, posts), desc);
            if (limit == null || limit >= list.size()) {
                return list;
            }
            if (limit == 0) {
                return new ArrayList<>();
            }
            if ("tree".equals(sort)) {
                return list.subList(0, limit);
            }
            int rootCount = 0;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).parent == null) {
                    rootCount++;
                }
                if (rootCount > limit) {
                    return list.subList(0, i);
                }
            }
            return list;
        }

        @SuppressWarnings("InnerClassTooDeeplyNested")
        private static final class PostTreeNode {
            private PostDetails value;
            private List<PostTreeNode> children = new ArrayList<>();

            private PostTreeNode(PostDetails value) {
                this.value = value;
            }

            private PostTreeNode() {
            }

            private static PostTreeNode tree(PostTreeNode node, List<PostDetails> posts) {
                if (node == null) {
                    node = new PostTreeNode();
                }
                final Iterator<PostDetails> iterator = posts.iterator();
                while (iterator.hasNext()) {
                    final PostDetails details = iterator.next();
                    if (details.parent == null || (node.value != null && details.parent == node.value.id)) {
                        node.children.add(new PostTreeNode(details));
                        iterator.remove();
                    }
                }
                for (int i = 0; i < node.children.size(); i++) {
                    node.children.set(i, tree(node.children.get(i), posts));
                }
                return node;
            }

            private static List<PostDetails> list(PostTreeNode node, boolean desc) {
                final List<PostDetails> list = new ArrayList<>();
                if (node.value != null) {
                    list.add(node.value);
                    if (desc) {
                        Collections.reverse(node.children);
                    }
                }
                for (PostTreeNode child : node.children) {
                    list.addAll(list(child, desc));
                }
                return list;
            }
        }
    }

    @SuppressWarnings("unused")
    private static final class UserUpdateRequest {
        private String about;
        private String user;
        private String name;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public UserUpdateRequest() {
        }

        public String getAbout() {
            return about;
        }

        public void setAbout(String about) {
            this.about = about;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @SuppressWarnings("unused")
    private static final class PostUpdateRequest {
        private int post;
        private String message;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public PostUpdateRequest() {
        }

        public int getPost() {
            return post;
        }

        public void setPost(int post) {
            this.post = post;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    @SuppressWarnings("unused")
    private static final class PostID {
        private int post;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public PostID() {
        }

        public int getPost() {
            return post;
        }

        public void setPost(int post) {
            this.post = post;
        }
    }

    @SuppressWarnings("unused")
    private static final class PostVote {
        private int vote;
        private int post;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public PostVote() {
        }

        public int getVote() {
            return vote;
        }

        public void setVote(int vote) {
            this.vote = vote;
        }

        public int getPost() {
            return post;
        }

        public void setPost(int post) {
            this.post = post;
        }
    }

    @SuppressWarnings("unused")
    private static final class ThreadID {
        private int thread;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public ThreadID() {
        }

        public int getThread() {
            return thread;
        }

        public void setThread(int thread) {
            this.thread = thread;
        }
    }

    @SuppressWarnings("unused")
    private static final class ThreadVote {
        private int vote;
        private int thread;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public ThreadVote() {
        }

        public int getVote() {
            return vote;
        }

        public void setVote(int vote) {
            this.vote = vote;
        }

        public int getThread() {
            return thread;
        }

        public void setThread(int thread) {
            this.thread = thread;
        }
    }

    @SuppressWarnings("unused")
    private static final class ThreadUpdateRequest {
        private String message;
        private String slug;
        private int thread;

        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public ThreadUpdateRequest() {
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }

        public int getThread() {
            return thread;
        }

        public void setThread(int thread) {
            this.thread = thread;
        }
    }
}
