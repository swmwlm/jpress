/**
 * Copyright (c) 2015-2016, Michael Yang 杨福海 (fuhai999@gmail.com).
 *
 * Licensed under the GNU Lesser General Public License (LGPL) ,Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.jpress.admin.controller;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.jfinal.aop.Before;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Page;

import io.jpress.Consts;
import io.jpress.core.JBaseCRUDController;
import io.jpress.core.interceptor.ActionCacheClearInterceptor;
import io.jpress.core.render.AjaxResult;
import io.jpress.interceptor.UCodeInterceptor;
import io.jpress.model.Content;
import io.jpress.model.Metadata;
import io.jpress.model.Taxonomy;
import io.jpress.model.User;
import io.jpress.model.query.ContentQuery;
import io.jpress.model.query.MappingQuery;
import io.jpress.model.query.MetaDataQuery;
import io.jpress.model.query.TaxonomyQuery;
import io.jpress.model.query.UserQuery;
import io.jpress.plugin.message.Actions;
import io.jpress.plugin.message.MessageKit;
import io.jpress.router.RouterMapping;
import io.jpress.router.RouterNotAllowConvert;
import io.jpress.router.converter.ContentRouter;
import io.jpress.template.TemplateUtils;
import io.jpress.template.TplModule;
import io.jpress.template.TplTaxonomyType;
import io.jpress.utils.StringUtils;

@RouterMapping(url = "/admin/content", viewPath = "/WEB-INF/admin/content")
@Before(ActionCacheClearInterceptor.class)
@RouterNotAllowConvert
public class _ContentController extends JBaseCRUDController<Content> {

	private String getModuleName() {
		return getPara("m");
	}

	private String getStatus() {
		return getPara("s");
	}

	@Override
	public void index() {

		TplModule module = TemplateUtils.currentTemplate().getModuleByName(getModuleName());
		setAttr("module", module);
		setAttr("delete_count", ContentQuery.me().findCountByModuleAndStatus(getModuleName(), Content.STATUS_DELETE));
		setAttr("draft_count", ContentQuery.me().findCountByModuleAndStatus(getModuleName(), Content.STATUS_DRAFT));
		setAttr("normal_count", ContentQuery.me().findCountByModuleAndStatus(getModuleName(), Content.STATUS_NORMAL));
		setAttr("count", ContentQuery.me().findCountInNormalByModule(getModuleName()));

		setAttr("tids", getPara("tids"));
		BigInteger[] tids = null;
		String[] tidStrings = getPara("tids", "").split(",");
		if (tidStrings != null && tidStrings.length > 0) {
			List<BigInteger> tidList = new ArrayList<BigInteger>();
			for (String stringid : tidStrings) {
				if (StringUtils.isNotBlank(stringid)) {
					tidList.add(new BigInteger(stringid));
				}
			}
			tids = tidList.toArray(new BigInteger[] {});
		}

		String keyword = getPara("k", "").trim();

		Page<Content> page = null;
		if (StringUtils.isNotBlank(getStatus())) {
			page = ContentQuery.me().paginateBySearch(getPageNumbere(), getPageSize(), getModuleName(), keyword,
					getStatus(), tids, null);
		} else {
			page = ContentQuery.me().paginateByModuleNotInDelete(getPageNumbere(), getPageSize(), getModuleName(),
					keyword, tids, null);
		}

		filterUI(tids);

		setAttr("page", page);
		render("index.html");
	}

	private void filterUI(BigInteger[] tids) {
		TplModule module = TemplateUtils.currentTemplate().getModuleByName(getModuleName());

		if (module != null) {
			List<TplTaxonomyType> types = module.getTaxonomyTypes();
			if (types != null && !types.isEmpty()) {
				HashMap<String, List<Taxonomy>> _taxonomyMap = new HashMap<String, List<Taxonomy>>();

				for (TplTaxonomyType type : types) {
					// 排除标签类的分类删选
					if (TplTaxonomyType.TYPE_SELECT.equals(type.getFormType())) {
						List<Taxonomy> taxonomys = TaxonomyQuery.me().findListByModuleAndTypeAsSort(getModuleName(),
								type.getName());
						processSelected(tids, taxonomys);
						_taxonomyMap.put(type.getTitle(), taxonomys);
					}
				}

				setAttr("_taxonomyMap", _taxonomyMap);
			}
		}
	}

	private void processSelected(BigInteger[] tids, List<Taxonomy> taxonomys) {
		if (taxonomys == null || taxonomys.isEmpty()) {
			return;
		}
		if (tids == null || tids.length == 0) {
			return;
		}
		for (Taxonomy t : taxonomys) {
			for (BigInteger id : tids) {
				if (t.getId().compareTo(id) == 0) {
					t.put("_selected", "selected=\"selected\"");
				}
			}
		}
	}

	@Before(UCodeInterceptor.class)
	public void trash() {
		Content c = ContentQuery.me().findById(getParaToBigInteger("id"));
		if (c != null) {
			c.setStatus(Content.STATUS_DELETE);
			c.saveOrUpdate();
			renderAjaxResultForSuccess("success");
		} else {
			renderAjaxResultForError("trash error!");
		}
	}

	@Before(UCodeInterceptor.class)
	public void draft() {
		Content c = ContentQuery.me().findById(getParaToBigInteger("id"));
		if (c != null) {
			c.setStatus(Content.STATUS_DRAFT);
			c.saveOrUpdate();
			renderAjaxResultForSuccess("success");
		} else {
			renderAjaxResultForError("trash error!");
		}
	}

	@Before(UCodeInterceptor.class)
	public void batchTrash() {
		BigInteger[] ids = getParaValuesToBigInteger("dataItem");
		int count = ContentQuery.me().batchTrash(ids);
		if (count > 0) {
			renderAjaxResultForSuccess("success");
		} else {
			renderAjaxResultForError("trash error!");
		}
	}

	@Before(UCodeInterceptor.class)
	public void batchDelete() {
		BigInteger[] ids = getParaValuesToBigInteger("dataItem");
		int count = ContentQuery.me().batchDelete(ids);
		if (count > 0) {
			renderAjaxResultForSuccess("success");
		} else {
			renderAjaxResultForError("trash error!");
		}
	}

	@Before(UCodeInterceptor.class)
	public void restore() {
		BigInteger id = getParaToBigInteger("id");
		Content c = ContentQuery.me().findById(id);
		if (c != null && c.isDelete()) {
			c.setStatus(Content.STATUS_DRAFT);
			c.setModified(new Date());
			c.saveOrUpdate();
			renderAjaxResultForSuccess("success");
		} else {
			renderAjaxResultForError("restore error!");
		}
	}

	@Before(UCodeInterceptor.class)
	public void delete() {
		BigInteger id = getParaToBigInteger("id");
		final Content c = ContentQuery.me().findById(id);
		if (c != null && c.isDelete()) {
			boolean isSuccess = Db.tx(new IAtom() {
				@Override
				public boolean run() throws SQLException {
					if (c.delete()) {
						MappingQuery.me().deleteByContentId(c.getId());
						return true;
					}
					return false;
				}
			});

			if (isSuccess) {
				renderAjaxResultForSuccess();
				return;
			}
		}
		renderAjaxResultForError();
	}

	@Override
	public void edit() {

		String moduleName = getModuleName();
		BigInteger contentId = getParaToBigInteger("id");

		Content content = ContentQuery.me().findById(contentId);
		if (content != null) {
			setAttr("content", content);
			moduleName = content.getModule();
		}

		TplModule module = TemplateUtils.currentTemplate().getModuleByName(moduleName);
		setAttr("module", module);

		String _editor = getCookie("_editor", "tinymce");
		setAttr("_editor", _editor);

		setAttr("urlPreffix", ContentRouter.getContentRouterPreffix(module));
		setAttr("urlSuffix", ContentRouter.getContentRouterSuffix(module));

		if (!Consts.MODULE_PAGE.equals(moduleName)) {
			String routerType = ContentRouter.getRouterType();
			if (StringUtils.isBlank(routerType) || ContentRouter.TYPE_DYNAMIC_ID.equals(routerType)
					|| ContentRouter.TYPE_STATIC_MODULE_ID.equals(routerType)
					|| ContentRouter.TYPE_STATIC_DATE_ID.equals(routerType)
					|| ContentRouter.TYPE_STATIC_PREFIX_ID.equals(routerType)) {
				setAttr("slugDisplay", " style=\"display: none\"");
			}
		}

		super.edit();
	}

	public void changeEditor() {
		String name = getPara();
		setCookie("_editor", name, Integer.MAX_VALUE);
		renderAjaxResultForSuccess();
	}

	private Content getContent() {
		Content content = getModel(Content.class);

		String text = content.getText();
		if (null != text && !"".equals(text)) {
			Document document = Jsoup.parse(text);
			if (null != document && document.body() != null) {
				content.setText(document.body().html().toString());
			}
		}

		if (content.getCreated() == null) {
			content.setCreated(new Date());
		}
		content.setModified(new Date());

		User user = getAttr(Consts.ATTR_USER);
		content.setUserId(user.getId());

		return content;
	}

	public List<BigInteger> getOrCreateTaxonomyIds(String moduleName) {
		TplModule module = TemplateUtils.currentTemplate().getModuleByName(moduleName);
		List<TplTaxonomyType> types = module.getTaxonomyTypes();
		List<BigInteger> tIds = new ArrayList<BigInteger>();
		for (TplTaxonomyType type : types) {
			if (TplTaxonomyType.TYPE_INPUT.equals(type.getFormType())) {
				String data = getPara("_" + type.getName());
				if (!StringUtils.isNotEmpty(data)) {
					continue;
				}
				String[] titles = data.split(",");
				if (titles != null && titles.length > 0) {
					List<Taxonomy> list = TaxonomyQuery.me().findListByModuleAndType(moduleName, type.getName());
					for (String title : titles) {
						BigInteger id = getIdFromList(title, list);
						if (id == null) {
							Taxonomy taxonomy = new Taxonomy();
							taxonomy.setTitle(title);
							taxonomy.setSlug(title);
							taxonomy.setContentModule(moduleName);
							taxonomy.setType(type.getName());
							taxonomy.save();
							id = taxonomy.getId();
						}
						tIds.add(id);
					}
				}
			} else if (TplTaxonomyType.TYPE_SELECT.equals(type.getFormType())) {
				BigInteger[] ids = getParaValuesToBigInteger("_" + type.getName());
				if (ids != null && ids.length > 0)
					tIds.addAll(Arrays.asList(ids));
			}
		}
		return tIds;
	}

	private BigInteger getIdFromList(String string, List<Taxonomy> list) {
		for (Taxonomy taxonomy : list) {
			if (string.equals(taxonomy.getSlug()))
				return taxonomy.getId();
		}
		return null;
	}

	@Before(UCodeInterceptor.class)
	@Override
	public void save() {

		final Content content = getContent();

		if (StringUtils.isBlank(content.getTitle())) {
			renderAjaxResultForError("内容标题不能为空！");
			return;
		}

		boolean isAddAction = content.getId() == null;

		String slug = content.getSlug();

		if (StringUtils.isBlank(slug)) {
			slug = content.getTitle();
		}

		if (slug != null) {
			if (StringUtils.isNumeric(slug)) {
				slug = "c" + slug; // slug不能为全是数字,随便添加一个字母，c代表content好了
			} else {
				slug = slug.replaceAll("(\\s+)|(\\.+)|(。+)|(…+)|[\\$,，？\\-?、；;:!]", "_");
				slug = slug.replaceAll("(?!_)\\pP|\\pS", "");
			}
			content.setSlug(slug);
		}

		String username = getPara("username");
		if (StringUtils.isNotBlank(username)) {
			User user = UserQuery.me().findUserByUsername(username);
			if (user == null) {
				renderAjaxResultForError("系统没有该用户：" + username);
				return;
			}
			content.setUserId(user.getId());
		}

		Content dbContent = ContentQuery.me().findBySlug(content.getSlug());
		if (dbContent != null && content.getId() != null && dbContent.getId().compareTo(content.getId()) != 0) {
			renderAjaxResultForError();
			return;
		}

		final HashMap<String, String> metas = new HashMap<String, String>();
		Map<String, String[]> requestMap = getParaMap();
		if (requestMap != null) {
			for (Map.Entry<String, String[]> entry : requestMap.entrySet()) {
				if (entry.getKey().startsWith("meta_")) {
					metas.put(entry.getKey().substring(5), entry.getValue()[0]);
				}
			}
		}

		boolean saved = Db.tx(new IAtom() {
			@Override
			public boolean run() throws SQLException {

				Content oldContent = null;
				if (content.getId() != null) {
					oldContent = ContentQuery.me().findById(content.getId());
				}

				if (!content.saveOrUpdate()) {
					return false;
				}

				List<BigInteger> ids = getOrCreateTaxonomyIds(content.getModule());
				if (ids != null && ids.size() > 0) {
					if (!MappingQuery.me().doBatchUpdate(content.getId(), ids.toArray(new BigInteger[0]))) {
						return false;
					}
				}

				for (Map.Entry<String, String> entry : metas.entrySet()) {

					Metadata metadata = MetaDataQuery.me().findByTypeAndIdAndKey(Content.METADATA_TYPE, content.getId(),
							entry.getKey());

					if (metadata == null) {
						metadata = new Metadata();
					}
					metadata.setMetaKey(entry.getKey());
					metadata.setMetaValue(entry.getValue());
					metadata.setObjectId(content.getId());
					metadata.setObjectType(Content.METADATA_TYPE);
					if (!metadata.saveOrUpdate()) {
						return false;
					}
				}

				MessageKit.sendMessage(Actions.CONTENT_COUNT_UPDATE, ids.toArray(new BigInteger[] {}));

				if (oldContent != null && oldContent.getTaxonomys() != null) {
					List<Taxonomy> taxonomys = oldContent.getTaxonomys();
					BigInteger[] taxonomyIds = new BigInteger[taxonomys.size()];
					for (int i = 0; i < taxonomys.size(); i++) {
						taxonomyIds[i] = taxonomys.get(i).getId();
					}
					MessageKit.sendMessage(Actions.CONTENT_COUNT_UPDATE, taxonomyIds);
				}

				return true;
			}
		});

		if (!saved) {
			renderAjaxResultForError();
			return;
		}

		if (isAddAction) {
			MessageKit.sendMessage(Actions.CONTENT_ADD, content);
		} else {
			MessageKit.sendMessage(Actions.CONTENT_UPDATE, content);
		}

		AjaxResult ar = new AjaxResult();
		ar.setErrorCode(0);
		ar.setData(content.getId());
		renderAjaxResult("save ok", 0, content.getId());
	}

}
