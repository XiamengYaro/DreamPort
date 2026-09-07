-- DreamPort v1.1.x 照片墙评论先审后发开关(先发后审为默认;开启后新评论 approved=false 待审)

ALTER TABLE dp_photo_comment ADD COLUMN approved BOOLEAN NOT NULL DEFAULT TRUE;
