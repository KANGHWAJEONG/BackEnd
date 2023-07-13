package com.example.demo.src.post;

import com.example.demo.src.category.CATEGORY;
import com.example.demo.src.image.PostImageReq;
import com.example.demo.src.post.community.CommunityPost;
import com.example.demo.src.post.community.GetCommunityPostRes;
import com.example.demo.src.post.generalModel.*;
import com.example.demo.src.post.community.CommunityPostingReq;
import com.example.demo.src.post.groupPurchase.GetGroupPurchasePostRes;
import com.example.demo.src.post.groupPurchase.GroupPurchasePost;
import com.example.demo.src.post.groupPurchase.GroupPurchasePostingReq;
import com.example.demo.src.post.generalModel.LikeReq;
import com.example.demo.src.post.recipe.GetRecipePostRes;
import com.example.demo.src.post.recipe.RecipePost;
import com.example.demo.src.post.recipe.RecipePostingReq;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.HashMap;

@Repository
public class PostDao {

    private JdbcTemplate jdbcTemplate;
    private ObjectMapper mapper;
    @Autowired
    public PostDao(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.mapper = new ObjectMapper();
    }

    /*	3.1 커뮤니티
    Post : postIdx; categoryIdx; userIdx; title; viewCount; likeCount; createAt; updateAt; url;
    GroupPurchaseDetail : groupPurchaseDetailIdx; postIdx; productName; productURL; singlePrice;
                          deliveryFee; members; deadline;hasExtension; calculated;
    CommunityDetail :     communityDetailIdx; postIdx; contents;
    RecipeDetail :        recipeDetailIdx; postIdx; contents; tag;
    PostingRes :          categoryIdx; userIdx; title; createAt; url;
     */

    // TODO : 사진 올리기 추가해야한다.
    // 글쓰기
    public PostingRes posting(int boardIdx, int categoryIdx, HashMap<String,Object> postingReq) {
        // 입력받은 정보를 general information, specific information으로 구분하는 작업
        // 그 중에서 general information을 Post general에 담는 과정
        PostingReq general = new PostingReq(
                (int)postingReq.get("userIdx"), (String)postingReq.get("title")
        );
        
        // Post table에 insert 하는 sql 문장과 그 파라미터, URL의 경우 'null'로 저장함.
        String sqlGeneral = "INSERT INTO Post(categoryIdx, userIdx, title, viewCount, likeCount, createAt, updateAt, url) VALUES (?,?,?,0,0,now(),now(),'null')";
        Object[] paramGeneral = {
                categoryIdx, general.getUserIdx(), general.getTitle()
        };
        // general 쿼리를 실행하는 부분
        this.jdbcTemplate.update(sqlGeneral, paramGeneral);
        // postIdx 값을 쉽게 사용하기 위해 정의함.
        String lastInsertIdQuery = "select last_insert_id()";
        int postIdx = this.jdbcTemplate.queryForObject(lastInsertIdQuery, int.class);
        // 공동구매, 커뮤니티, 레시피 세 경우에 대해, 각 테이블에 정보를 저장하기 위해 sql문과 param을 정의함.
        String sqlSpecific = "";
        Object[] paramSpecific = null;
        //커뮤니티
        if (boardIdx == 1) {
            CommunityPostingReq posting = new CommunityPostingReq(postIdx, (String)postingReq.get("contents"));
            sqlSpecific = "INSERT INTO CommunityDetail(communityDetailIdx, postIdx, contents, heartCount) VALUES (" + postIdx + "," + postIdx + ",?, 0)";
            paramSpecific = new Object[]{posting.getContents()};
        }
        //공동구매
        else if (boardIdx == 2) {
            GroupPurchasePostingReq posting = new GroupPurchasePostingReq(postingReq);
            sqlSpecific = "INSERT INTO GroupPurchaseDetail(groupPurchaseDetailIdx, postIdx, productName, productURL, singlePrice, deliveryFee, " +
                    "members, deadline,hasExtension, calculated) VALUES (" + postIdx + "," + postIdx + ",?,?,?,?,?,?,?,?)";
            paramSpecific = new Object[]{posting.getProductName(), posting.getProductURL(), posting.getSinglePrice(),
                    posting.getDeliveryFee(), posting.getMembers(), posting.getDeadline(), posting.isHasExtension(), posting.isCalculated()
            };
        }
        //레시피
        else if (boardIdx == 3) {
            RecipePostingReq posting = mapper.convertValue(postingReq, RecipePostingReq.class);
            sqlSpecific = "INSERT INTO RecipeDetail(recipeDetailIdx,postIdx, contents, tag) VALUES(" + postIdx + "" + postIdx + ",?,?)";
            paramSpecific = new Object[]{(String)postingReq.get("contents"), (String)postingReq.get("tag")};
        }
        // 오류 처리
        else {
            System.out.println("잘못된 카테고리 이름입니다.");
            return null;
        }
        // validation : 오류 처리
        if (sqlSpecific.equals("") || paramSpecific == null) {
            System.out.println("쿼리 또는 파라미터가 제대로 설정되지 않았습니다.");
            return null;
        }
        // 공동구매, 커뮤니티, 레시피 sql과 param을 이용해 쿼리문 실행
        this.jdbcTemplate.update(sqlSpecific, paramSpecific);
        // 반환할 응답 생성
        PostingRes postingRes = new PostingRes(postIdx, categoryIdx, general.getCategory(), general.getUserIdx(), general.getTitle(), "null");
        // 응답 반환
        return postingRes;
    }

    //TODO : 사진 불러오기 추가해야 한다.

    // 글보기
    public Object getPost(int categoryIdx, GetPostReq getPostReq) {
        // 세 게시판의 글을 한번에 처리하기 위한 변수 설정
        // 기본정보와 detail 정보 불러오기
        Object generalPost = _getPost(getPostReq.getPostIdx()),
                detailPost = _getDetailPost(categoryIdx,getPostReq.getPostIdx());
        // 기본 정보(Post의 정보)를 불러오기 위한 sql문
        
        // 조회수 1 증가시키기 위해 sql문 작성 및 실행
        String viewUpdateSql = "UPDATE Post set viewCount = viewCount+1 WHERE postIdx = "+getPostReq.getPostIdx();
        this.jdbcTemplate.update(viewUpdateSql);

        // Post와 detail의 정보를 합친 후 리턴하기
        if (categoryIdx == 10) {
            return new GetCommunityPostRes((Post)generalPost, (CommunityPost)detailPost);
        }
        else if(categoryIdx == 20) {
            return new GetGroupPurchasePostRes((Post)generalPost,(GroupPurchasePost)detailPost);
        }
        else if (categoryIdx == 30) {
            return new GetRecipePostRes((Post)generalPost, (RecipePost)detailPost);
        }
        else return null;
    }

    public boolean scrapPost(LikeReq likeReq){
        // 어떤 유저가 어떤 게시글에 스크랩을 눌렀는가를 LikedPost 테이블에 기록하는 과정
        String sql = "INSERT INTO LikedPost(postIdx, userIdx) VALUES (?,?)";
        Object[] param = {likeReq.getPostIdx(),likeReq.getUserIdx()};
        // 기록에 실패한 경우
        if(this.jdbcTemplate.update(sql,param) == 0) {
            System.out.println("LikedPost 테이블에 기록하지 못했습니다.");
            return false;
        }
        // 기록에 성공하고, 해당 게시글의 좋아요 수를 1 증가시키는 과정
        else {
            String likeCountIncreaseSql = "UPDATE Post SET likeCount = likeCount + 1 WHERE postIdx = "+likeReq.getPostIdx();
            // 좋아요 수를 증가시키는 것을 실패한 경우
            if(this.jdbcTemplate.update(likeCountIncreaseSql) == 0) return false;
            // 좋아요 수를 증가시키고, 해당 게시글의 좋아요 수를 불러오는 과정
            else {
                return true;
            }
        }
    }

    public boolean heartPost(HeartPostReq heartPostReq){
        String sql = "UPDATE CommunityDetail SET heartCount = heartCount+1 WHERE postIdx = "+heartPostReq.getPostIdx();
        if(this.jdbcTemplate.update(sql) == 0) return false;
        else return true;
    }
    public boolean deletePost(DeleteReq deleteReq) {
        int general = 0, detail = 0;
        int boardIdx = CATEGORY.getNumber(deleteReq.getBoard());
        String board = boardIdx == 10 ? "Community" :
                (boardIdx == 20 ? "GroupPurchase" :
                        (boardIdx == 30 ? "Recipe" : null));
        if (board == null) return false;

        String deleteDetailSql = "DELETE FROM "+board+"Detail WHERE postIdx = "+deleteReq.getPostIdx();
        String deleteGeneralSql = "DELETE FROM Post WHERE postIdx = "+deleteReq.getPostIdx();
        detail = this.jdbcTemplate.update(deleteDetailSql);
        general = this.jdbcTemplate.update(deleteGeneralSql);

        if(detail * general == 1) return true;
        else return false;
    }

    public int getLikeCount(int postIdx) {
        int likeCount = -1;
        try{
            String sql = "SELECT likeCount FROM Post WHERE postIdx = "+postIdx;
            likeCount = this.jdbcTemplate.queryForObject(sql,int.class);
            }catch(Exception e){
                return -1;
            }
        return likeCount;
    }

    public Timestamp extendDeadLine(int postIdx){
        try{
            String sql = "SELECT deadline FROM CommunityDetail WHERE postIdx = "+postIdx;
            return this.jdbcTemplate.queryForObject(sql,Timestamp.class);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean postImage(PostImageReq postImageReq) {
        String sql = "INSERT INTO Image(postIdx, path) VALUES";
        for (String path : postImageReq.getPaths()) {
            sql += "(" + "??<<" + "," + path + ")";
        }
        return this.jdbcTemplate.update(sql) == 1 ? true : false;
    }

    public Post _getPost(int postIdx){
        String sql = "SELECT * FROM Post WHERE postIdx = " + postIdx;
        return this.jdbcTemplate.queryForObject(sql, (rs,rowNum)-> new Post(
                rs.getInt("postIdx"),
                rs.getInt("categoryIdx"),
                rs.getInt("userIdx"),
                rs.getString("title"),
                rs.getInt("viewCount"),
                rs.getInt("likeCount"),
                rs.getTimestamp("createAt"),
                rs.getTimestamp("updateAt"),
                rs.getString("url")));
    }
    public Object _getDetailPost(int boardIdx, int postIdx){
        // 공동구매 detail 정보 불러오기
        Object detailPost;
        if(boardIdx == 20) {
            String qry = "SELECT * FROM GroupPurchaseDetail WHERE postIdx = "+postIdx;
            detailPost = this.jdbcTemplate.queryForObject(qry, (rs, rowNum) -> new GroupPurchasePost(
                    rs.getInt("groupPurchaseDetailIdx"),
                    rs.getString("productName"),
                    rs.getString("productURL"),
                    rs.getDouble("singlePrice"),
                    rs.getDouble("deliveryFee"),
                    rs.getInt("members"),
                    rs.getTimestamp("deadline"),
                    rs.getBoolean("hasExtension"),
                    rs.getBoolean("calculated")
            ));
            return (GroupPurchasePost)detailPost;
        }
        // 커뮤니티 detail 정보 불러오기
        else if (boardIdx == 10) {
            String qry = "SELECT * FROM CommunityDetail WHERE postIdx = "+postIdx;
            detailPost = this.jdbcTemplate.queryForObject(qry, (rs, rowNum) -> new CommunityPost(
                    rs.getInt("communityDetailIdx"),
                    rs.getString("contents")
            ));
            // Post와 detail의 정보를 합친 후 리턴하기
            return (CommunityPost)detailPost;
        }
        // 레시피 detail 정보 불러오기
        else if (boardIdx == 30) {
            String qry = "SELECT * FROM RecipeDetail WHERE postIdx = " + postIdx;
            detailPost = this.jdbcTemplate.queryForObject(qry, (rs, rowNum) -> new RecipePost(
                    rs.getInt("recipeDetailIdx"),
                    rs.getString("contents"),
                    rs.getString("tag")
            ));
            return (RecipePost)detailPost;
        }
        else return null;
    }


}