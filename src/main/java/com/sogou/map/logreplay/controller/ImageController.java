package com.sogou.map.logreplay.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.multiaction.NoSuchRequestHandlingMethodException;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sogou.map.logreplay.bean.Avatar;
import com.sogou.map.logreplay.bean.Image;
import com.sogou.map.logreplay.bean.Image.AvatarType;
import com.sogou.map.logreplay.controller.base.BaseController;
import com.sogou.map.logreplay.dao.base.QueryParamMap;
import com.sogou.map.logreplay.exception.LogReplayException;
import com.sogou.map.logreplay.service.AvatarService;
import com.sogou.map.logreplay.service.ImageService;
import com.sogou.map.logreplay.util.AuthUtil;
import com.sogou.map.logreplay.util.ImageUtil;

@Controller
@RequestMapping("/image")
public class ImageController extends BaseController {
	
	public static final String DEFAULT_IMAGE_FORMAT = "jpg";
	
	@Autowired
	private ImageService imageService;
	
	@Autowired
	private AvatarService avatarService;
	
	/**
	 * ��id��ȡͼƬ
	 */
	@RequestMapping("{id:\\d+}")
	public void getImage(
			@PathVariable("id") Long id,
			HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		Image image = null;
		File imageFile = null;
		if(id == null 
				|| (image = imageService.getImageById(id)) == null
				|| !(imageFile = new File(image.getFilepath())).exists()) {
			throw new NoSuchRequestHandlingMethodException(request);
		}
		// TODO �ӻ���ͷ
		InputStream input = null;
		OutputStream output = null;
		try {
			input = new FileInputStream(imageFile);
			output = response.getOutputStream();
			IOUtils.copy(input, output);
			output.flush();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(input);
			IOUtils.closeQuietly(output);
		}
		
	}
	
	/**
	 * ��ȡ�û�ͷ��
	 */
	@RequestMapping(value = "avatar", method = RequestMethod.GET)
	public void getAvatar(
			Long userId,
			@RequestParam(defaultValue = Image.TYPE_MIDDLE) String type,
			HttpServletRequest request,
			HttpServletResponse response
			) throws ServletException, IOException {
		if(userId == null) {
			userId = AuthUtil.getCurrentUser().getId();
		}
		Avatar avatar = avatarService.getAvatarByUserIdAndType(userId, type);
		Image image = avatar != null? imageService.getImageById(avatar.getImageId()): null;
		File imageFile = null;
		if(image == null || !(imageFile = new File(image.getFilepath())).exists()) {
			// �滻��Ĭ��ͼƬ
			response.sendRedirect(request.getContextPath() + Avatar.DEFAULT_AVATAR);
			return;
		}
		InputStream input = null;
		OutputStream output = null;
		try {
			input = new FileInputStream(imageFile);
			output = response.getOutputStream();
			IOUtils.copy(input, output);
			output.flush();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(input);
			IOUtils.closeQuietly(output);
		}
	}
	
	/**
	 * ����rawImage���ú��ύ��ͷ��
	 */
	@ResponseBody
	@RequestMapping(value = "avatar", method = RequestMethod.POST)
	public ModelMap submitAvatar(
			@RequestParam Long imageId, 
			@RequestParam int left, 
			@RequestParam int top, 
			@RequestParam int width, 
			@RequestParam int height,
			@RequestParam int imgWidth,
			@RequestParam int imgHeight) throws IOException {
		if(left < 0 || top < 0 || width <= 0 || height <= 0 || imgWidth <= 0 || imgHeight <= 0 || imageId == null) {
			throw LogReplayException.invalidParameterException("Parameters are invalid!");
		}
		Image image = imageService.getImageById(imageId);
		if(image == null ) {
			throw LogReplayException.notExistException("Image[%d] does not exist!", imageId);
		}
		File imageFile = new File(image.getFilepath());
		if(!imageFile.exists()) {
			throw LogReplayException.notExistException("Failed to find file of Image[%d]", image.getId());
		}
		// �г�3�ֳߴ��ͷ��
		double ratio = image.getWidth() * 1D / imgWidth;
		left = Double.valueOf(left * ratio).intValue();
		top = Double.valueOf(top * ratio).intValue();
		width = Double.valueOf(width * ratio).intValue();
		height = Double.valueOf(height * ratio).intValue();
		
		BufferedImage cuttedImage = ImageIO.read(imageFile).getSubimage(left, top, width, height);
		List<Image> avatarImageList = buildAvatarImages(cuttedImage, DEFAULT_IMAGE_FORMAT);
		
		// ��У��ͼ�鲢�����Ѵ��ڵ�ͼƬ
		List<String> checksumList = Lists.transform(avatarImageList, new Function<Image, String>() {
			@Override
			public String apply(Image image) {
				return image.getChecksum();
			}
		});
		List<Image> prevImageList = CollectionUtils.isEmpty(checksumList)? Collections.<Image>emptyList()
				: imageService.getImageListResult(new QueryParamMap().addParam("checksum__in", checksumList));
		Map<String, Image> prevImageMap = Maps.uniqueIndex(prevImageList, new Function<Image, String>(){
			@Override
			public String apply(Image image) {
				return image.getChecksum();
			}
		});
		
		Iterator<Image> avatarImageIter = avatarImageList.iterator();
		while(avatarImageIter.hasNext()) {
			if(prevImageMap.containsKey(avatarImageIter.next().getChecksum())) {
				avatarImageIter.remove();
			}
		}
		
		try {
			for(Image avatarImage: avatarImageList) {
				persistImage(avatarImage);
			}
//			imageService.batchSaveImageList(avatarImageList);
			for(Image avatarImage: avatarImageList) {
				imageService.createImage(avatarImage);
			}
			
			avatarImageList.addAll(prevImageList);
			List<Avatar> avatarList = buildAvatarList(avatarImageList);
			avatarService.renewAvatars(avatarList, AuthUtil.getCurrentUser().getId());
			return successResult("Avatars are updated successfully!");
		} catch (Exception e) {
			e.printStackTrace();
			throw LogReplayException.operationFailedException("Failed to update avatars!");
		}
	}
	
	private List<Avatar> buildAvatarList(List<Image> avatarImageList) {
		List<Avatar> avatarList = new ArrayList<Avatar>();
		Long userId = AuthUtil.getCurrentUser().getId();
		for(Image image: avatarImageList) {
			avatarList.add(new Avatar(userId, image.getId(), image.getType()));
		}
		return avatarList;
	}
	
	private List<Image> buildAvatarImages(BufferedImage image, String format) throws IOException {
		if(StringUtils.isEmpty(format)) {
			format = DEFAULT_IMAGE_FORMAT;
		}
		List<Image> avatarList = new ArrayList<Image>();
		int width = image.getWidth(), height = image.getHeight();
		Long creatorId = AuthUtil.getCurrentUser().getId();
		for(AvatarType avatarType: AvatarType.values()) {
			double scaleX = avatarType.getWidth() * 1D / width;
			double scaleY = avatarType.getHeight() * 1D / height;
			BufferedImage zoomedImage = ImageUtil.zoomImage(image, scaleX, scaleY);
			byte[] bytes = ImageUtil.toByteArray(zoomedImage, format);
			
			Image avatar = new Image.Builder()
				.creatorId(creatorId)
				.format(format)
				.width(avatarType.getWidth())
				.height(avatarType.getHeight())
				.type(avatarType.name())
				.bytes(bytes)
				.build();
			avatarList.add(avatar);
		}
		return avatarList;
	}

	/**
	 * �ϴ�rawImage
	 */
	@ResponseBody
	@RequestMapping(value = "/upload", method = RequestMethod.POST, params = "type=raw")
	public Map<String, Object> uploadRawImage(MultipartFile file) {
		try {
			Image image = buildRawImage(file, DEFAULT_IMAGE_FORMAT);
			Image prevImage = imageService.getImageByChecksum(image.getChecksum());
			if(prevImage != null && prevImage.getSize().equals(image.getSize())) {
				return successResult(prevImage);
			}
			persistImage(image);
			imageService.createImage(image);
			return successResult(image);
		} catch (LogReplayException lre) {
			throw lre;
		} catch (Exception e) {
			e.printStackTrace();
			throw LogReplayException.operationFailedException("Failed to persist image[%s]", file.getName());
		}
	}
	
	private Image buildRawImage(MultipartFile file, String format) throws IOException {
		if(StringUtils.isEmpty(format)) {
			format = FilenameUtils.getExtension(file.getName());
		}
		BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
		byte[] bytes = ImageUtil.toByteArray(bufferedImage, format);
		
		return new Image.Builder()
			.creatorId(AuthUtil.getCurrentUser().getId())
			.format(format)
			.bytes(bytes)
			.width(bufferedImage.getWidth())
			.height(bufferedImage.getHeight())
			.type(Image.TYPE_RAW)
			.build();
	}
	
	private void persistImage(Image image) throws IOException {
		File imageFile = new File(image.getFilepath());
		FileUtils.writeByteArrayToFile(imageFile, image.getBytes());
	}
	
	
}