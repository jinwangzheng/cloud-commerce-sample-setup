/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package org.training.service;

public interface TrainingService
{
	String getHybrisLogoUrl(String logoCode);

	void createLogo(String logoCode);
}
